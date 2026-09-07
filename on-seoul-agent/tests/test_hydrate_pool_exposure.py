"""QA 회귀 — rrf_hydrate_pool 확장이 하류 "노출 건수 불변" 계약을 지키는지.

vector.results 는 이제 rrf_hydrate_pool(30) 깊이의 *후보 풀*이다. 하류에서 건수를
보고하거나 LLM 에 넣는 모든 경로는 최종 절단값(rrf_top_k_final=10) 기준이어야
과대 보고가 없다. 이 파일은 pre_answer_gate 절단 안전망과 critic 요약 경로를 고정한다.

무진전 가드(prev_result_signature)의 엣지-노드 순서 불변식도 함께 고정한다.
"""

from unittest.mock import AsyncMock, MagicMock, patch

from agents.graph import AgentGraph, _trace_completion_metadata
from agents.nodes.retrieval import RetrievalNodes
from agents.retrieval_critic import build_result_summary
from core.config import settings
from schemas.critic import ReplanHint
from schemas.state import ActionType, IntentType
from tests.helpers import (
    make_agent_state,
    make_ai_session,
    make_answer_agent,
    make_intake_router,
    run_graph,
)


def _pool(n: int, *, klass: str = "문화체험") -> list[dict]:
    return [
        {
            "service_id": f"P{i:03d}",
            "service_name": f"서비스{i}",
            "area_name": "광진구",
            "max_class_name": klass,
        }
        for i in range(n)
    ]


def _nodes() -> RetrievalNodes:
    return AgentGraph(answer_agent=make_answer_agent())._nodes


# ---------------------------------------------------------------------------
# 1. critic 입력 요약 — 후보 풀 깊이가 LLM 에 새면 안 된다
# ---------------------------------------------------------------------------


class TestCriticSummaryUsesExposedHits:
    """critic 은 재시도 여부를 결정하는 LLM 이다. 건수가 부풀면 판단이 뒤집힌다.

    build_result_summary 는 vector.results 길이를 그대로 쓴다. 풀이 30 으로 넓어진
    뒤에는 (a) "vector 30" 으로 3배 과대 보고되고 (b) 게이트가 전부 떨어뜨려 hydrated
    가 0건일 때 total 이 sql+vector 폴백으로 계산돼 "총 30건" 이 된다 — 실제로는
    답변에 쓸 행이 하나도 없는데 critic 에게는 풍족해 보인다.
    """

    def test_vector_count_capped_at_final_cut(self):
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            vector_results=[{"service_id": f"P{i}"} for i in range(settings.rrf_hydrate_pool)],
            hydrated_services=_pool(5),
        )
        summary = build_result_summary(state)
        assert f"vector {settings.rrf_top_k_final}" in summary, summary

    def test_zero_hit_after_gate_is_not_reported_as_full_pool(self):
        """게이트가 전부 제거해 0건인데 critic 에게 "총 30건" 이라고 말하면 안 된다."""
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            vector_results=[{"service_id": f"P{i}"} for i in range(settings.rrf_hydrate_pool)],
            hydrated_services=[],
        )
        summary = build_result_summary(state)
        assert f"총 {settings.rrf_hydrate_pool}건" not in summary, summary


class TestTraceSignalsUseExposedHits:
    """L1 평가 trace 신호도 최종 절단값 기준이어야 규칙 라벨(THIN/ZERO_HIT)이 안 뒤틀린다."""

    def test_trace_vector_hits_capped_at_final_cut(self):
        state = make_agent_state(
            vector_results=[
                {"service_id": f"P{i}"} for i in range(settings.rrf_hydrate_pool)
            ],
        )
        meta = _trace_completion_metadata(state)
        assert meta["vector_hits"] == settings.rrf_top_k_final
        assert meta["total_hits"] == settings.rrf_top_k_final


# ---------------------------------------------------------------------------
# 2. pre_answer_gate 절단 안전망 — 자각 패스 비대상/예외 경로
# ---------------------------------------------------------------------------


class TestPoolCutSafetyNet:
    async def test_gap_oos_path_truncates_pool(self):
        """attribute_gap(OUT_OF_SCOPE)은 _is_retrieve_path 비대상 — 안전망이 잘라야 한다.

        answer 의 상세형 경로가 len(all_results) 로 "외 N건" 을 만들기 때문에
        30건이 새면 "외 25건" 같은 fetch 폭 의존 숫자가 사용자에게 나간다.
        """
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.OUT_OF_SCOPE,
            out_of_scope_type="attribute_gap",
            vector_sub_intent="attribute_gap",
            hydrated_services=_pool(settings.rrf_hydrate_pool),
        )
        out = await _nodes().pre_answer_gate_node(state)
        assert len(out["hydration"]["hydrated_services"]) == settings.rrf_top_k_final
        # 자각 패스 비대상이므로 품질/큐레이션 슬롯은 여전히 None(기존 계약 불변).
        assert out["result_quality"] is None
        assert out["curated_display"] is None

    async def test_gate_exception_still_truncates_pool(self):
        """게이트 점검이 예외로 죽어도 후보 풀이 하류로 새지 않는다."""
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=_pool(settings.rrf_hydrate_pool),
        )
        with patch(
            "agents.nodes.retrieval.apply_structured_gate",
            side_effect=RuntimeError("게이트 폭발"),
        ):
            out = await _nodes().pre_answer_gate_node(state)
        assert len(out["hydration"]["hydrated_services"]) == settings.rrf_top_k_final
        assert out["result_quality"] is None  # best-effort 격리 유지

    async def test_operational_detail_focal_row_is_pool_head(self):
        """operational_detail 은 rows[0] 를 focal 로 쓴다 — 절단이 순서를 바꾸면 안 된다."""
        rows = _pool(settings.rrf_hydrate_pool)
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            vector_sub_intent="operational_detail",
            hydrated_services=rows,
        )
        # OnDataReader 는 가짜 인스턴스를 주입해 격리한다(RetrievalNodes 설계 기준 ④).
        # nodes._retrieval._ondata 는 프로세스 공유 default_reader 싱글톤이므로 거기에
        # 속성을 직접 대입하면 이후 모든 테스트로 오염이 번진다(실제로
        # test_ondata_reader_adversarial 의 "bare instance" 가드 2건이 깨졌다).
        fake_reader = MagicMock()
        fake_reader.fetch_detail_content = AsyncMock(return_value="운영 상세 본문")
        nodes = _nodes()
        nodes._retrieval._ondata = fake_reader
        out = await nodes.pre_answer_gate_node(state)
        kept = out["hydration"]["hydrated_services"]
        assert kept[0]["service_id"] == rows[0]["service_id"]
        assert len(kept) == settings.rrf_top_k_final


# ---------------------------------------------------------------------------
# 3. 무진전 가드 — 엣지-노드 순서 불변식 + E2E
# ---------------------------------------------------------------------------


class TestNoProgressGuardOrdering:
    async def test_pre_answer_gate_never_writes_prev_signature(self):
        """가드 자기무효 방지: 시그니처 writer 는 retry_prep 단독이어야 한다.

        pre_answer_gate_node 가 슬롯을 쓰면 조건부 엣지는 머지 후 상태를 보므로
        "직전 라운드" 값이 사라져 가드가 항상 참이 된다.
        """
        state = make_agent_state(
            intent=IntentType.VECTOR_SEARCH,
            action=ActionType.RETRIEVE,
            hydrated_services=_pool(3),
        )
        out = await _nodes().pre_answer_gate_node(state)
        assert "prev_result_signature" not in out

    async def test_identical_second_round_calls_critic_only_once(self):
        """E2E: 동일 결과가 반복되면 2라운드에서 critic LLM 을 다시 부르지 않는다.

        1라운드 thin(2건) → critic REPLAN → retry_prep → 2라운드 동일 2건 →
        무진전 가드가 answer 직행. critic 호출은 1회로 끝난다.
        """
        rows = [
            {"service_id": "A", "service_name": "가", "area_name": "광진구"},
            {"service_id": "B", "service_name": "나", "area_name": "광진구"},
        ]
        intake, router = make_intake_router(
            intent=IntentType.VECTOR_SEARCH, area_name="광진구"
        )
        critic = MagicMock()
        critic.critique = AsyncMock(
            return_value={
                "critic_decision": "REPLAN",
                "critic_replan_hint": ReplanHint(
                    drop_filters=["area_name"], reason="지역 완화"
                ).model_dump(exclude_none=True),
                "critic_rationale": "더 넓게",
            }
        )
        vector_agent = MagicMock()
        vector_agent.search = AsyncMock(
            return_value={
                "vector": {"results": [{"service_id": r["service_id"]} for r in rows]},
                "plan": {"refined_query": "정제"},
            }
        )
        with (
            patch(
                "agents.hydration_node.hydrate_services",
                AsyncMock(return_value=list(rows)),
            ),
            patch.object(settings, "enable_retrieval_critic", True),
        ):
            graph = AgentGraph(
                intake=intake,
                router=router,
                vector_agent=vector_agent,
                answer_agent=make_answer_agent("답변"),
                critic=critic,
            )
            result = await run_graph(
                graph,
                make_agent_state(area_name=["광진구"]),
                data_session=make_ai_session(),
                ai_session=make_ai_session(),
            )
        path = result["node_path"]
        assert path.count("pre_answer_gate") == 2, path
        assert result["retry_count"] == 1
        assert critic.critique.await_count == 1, (
            f"무진전 가드가 2회차 critic 승격을 막지 못함: {path}"
        )
