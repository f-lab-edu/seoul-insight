"""무진전(no-progress) 가드 — 재시도가 동일 결과를 내면 critic/재검색을 건너뛴다.

retry_prep_node 가 리셋 직전 결과의 service_id 시그니처를 prev_result_signature 에
적재하고, 다음 라운드의 route_pre_answer_gate 가 같은 시그니처를 보면 answer_node 로
직행한다(critic LLM·재검색 낭비 차단). 시그니처가 바뀌었으면 기존 판정 그대로.
"""

from unittest.mock import MagicMock, patch

from agents.graph import AgentGraph
from agents.nodes import GraphNodes
from agents.nodes._shared import result_signature
from core.config import settings
from schemas.state import ActionType, IntentType
from tests.helpers import make_agent_state, make_answer_agent


def _nodes(critic=None) -> GraphNodes:
    return AgentGraph(answer_agent=make_answer_agent(), critic=critic)._nodes


def _rows(*ids: str) -> list[dict]:
    return [{"service_id": sid, "service_name": sid} for sid in ids]


class TestResultSignature:
    def test_signature_reflects_ids_and_order(self):
        assert result_signature(_rows("A", "B")) != result_signature(_rows("B", "A"))
        assert result_signature(_rows("A", "B")) == result_signature(_rows("A", "B"))

    def test_empty_and_none_are_empty_string(self):
        assert result_signature([]) == ""
        assert result_signature(None) == ""


class TestRetryPrepRecordsSignature:
    async def test_retry_prep_records_current_round_signature(self):
        nodes = _nodes()
        rows = _rows("A", "B")
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=rows,
            retry_count=0,
        )
        update = await nodes.retry_prep_node(state)
        assert update["prev_result_signature"] == result_signature(rows)


class TestNoProgressGuard:
    def test_same_signature_after_retry_goes_straight_to_answer(self):
        """재시도했는데 동일 결과 → critic 미호출·재검색 없이 answer 직행."""
        rows = _rows("A", "B")
        nodes = _nodes(critic=MagicMock())
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=rows,
            retry_count=1,
            prev_result_signature=result_signature(rows),
            result_quality={"thin": True},  # 원래라면 critic 승격 신호
        )
        with patch.object(settings, "enable_retrieval_critic", True):
            assert nodes.route_pre_answer_gate(state) == "answer_node"

    def test_changed_signature_still_escalates(self):
        """결과가 달라졌으면(진전 있음) 기존 판정 그대로 critic 승격."""
        nodes = _nodes(critic=MagicMock())
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=_rows("C"),
            retry_count=1,
            prev_result_signature=result_signature(_rows("A", "B")),
            result_quality={"thin": True},
        )
        with patch.object(settings, "enable_retrieval_critic", True):
            assert nodes.route_pre_answer_gate(state) == "retrieval_critic_node"

    def test_first_round_never_guarded(self):
        """retry_count=0 라운드는 비교 대상이 없어 가드가 개입하지 않는다."""
        nodes = _nodes(critic=MagicMock())
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=[],
            retry_count=0,
            prev_result_signature="",  # 0건 시그니처와 우연히 일치해도 무시
        )
        with patch.object(settings, "enable_retrieval_critic", True):
            assert nodes.route_pre_answer_gate(state) == "retrieval_critic_node"

    def test_zero_hits_twice_still_escalates_to_critic(self):
        """0건 → 재시도 → 또 0건은 무진전 가드 *대상이 아니다*.

        빈 시그니처("")는 truthy 검사로 제외한다. 0건→0건 상황은 필터가 이미 전부
        드롭된 상태라 결과를 바꿀 수 있는 유일한 수단이 critic 의 reformulate_query 다.
        가드가 여기까지 삼키면 그 회복 경로가 사라진다(0건 신호로 critic 을 부르던
        기존 동작 상실). 상한은 예산 백스톱이 잡으므로 무한 루프 위험은 없다.
        """
        nodes = _nodes(critic=MagicMock())
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=[],
            retry_count=1,
            prev_result_signature="",
        )
        with patch.object(settings, "enable_retrieval_critic", True):
            assert nodes.route_pre_answer_gate(state) == "retrieval_critic_node"

    def test_zero_hits_twice_deterministic_path_answers(self):
        """critic 비활성이면 0건 재시도는 기존 1회 캡(retry_count==0)에 걸려 answer.

        무진전 가드와 무관한 기존 결정적 경로 회귀 가드다.
        """
        nodes = _nodes()
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=[],
            retry_count=1,
            prev_result_signature="",
        )
        with patch.object(settings, "enable_retrieval_critic", False):
            assert nodes.route_pre_answer_gate(state) == "answer_node"
