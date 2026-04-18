from typing import Any, TypedDict


class AgentState(TypedDict):
    user_id: str
    session_id: str
    message: str  # 사용자 원본 질문
    intent: str | None  # SQL_SEARCH / VECTOR_SEARCH / MAP / FALLBACK
    refined_query: str | None  # Vector Agent가 정제한 질의
    sql_results: list[dict[str, Any]] | None  # SQL Agent 결과
    vector_results: list[dict[str, Any]] | None  # Vector Agent 결과
    map_results: dict[str, Any] | None  # map_search GeoJSON 결과
    answer: str | None  # Answer Agent가 생성한 최종 답변
    error: str | None  # 오류 메시지 (있을 경우)
