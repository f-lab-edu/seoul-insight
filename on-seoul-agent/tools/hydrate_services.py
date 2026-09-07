"""Hydrate Services Tool — service_id 리스트로 public_service_reservations 원본 조회.

service_embeddings(on_ai DB)는 검색 인덱스로만 쓰고, 답변 컨텍스트는
public_service_reservations(on_data DB)의 최신 원본에서 직접 조회한다.
임베딩 시점의 stale metadata(service_status·receipt_*_dt 등)를 우회하기 위함.

SQL Injection 방지:
    service_id 값은 단일 ARRAY bind 파라미터로 전달한다.
    SQL 템플릿에 service_id 값을 직접 삽입하지 않는다.
"""

import logging

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from tools._result_columns import PUBLIC_SERVICE_RESERVATIONS_COLUMNS

logger = logging.getLogger(__name__)

_RESULT_COLUMNS = PUBLIC_SERVICE_RESERVATIONS_COLUMNS

# 누락 로그에 남길 service_id 최대 개수(로그 폭주 방지).
_MISSING_LOG_LIMIT = 10


async def hydrate_services(
    session: AsyncSession,
    service_ids: list[str],
) -> list[dict]:
    """service_id 리스트로 public_service_reservations 원본 행을 조회한다.

    입력 순서(검색 순위)를 그대로 유지하여 반환한다.
    원본에 없거나 soft-delete된 service_id는 결과에서 제외한다.

    Parameters
    ----------
    session:
        on_data_reader 계정 AsyncSession (SELECT 전용).
    service_ids:
        조회 대상 service_id 리스트. 빈 리스트면 DB 호출 없이 빈 리스트 반환.

    Returns
    -------
    list[dict]
        _RESULT_COLUMNS 컬럼을 가진 딕셔너리 리스트.
        입력 순서를 보존하며, 원본 누락분은 제외된다.
    """
    if not service_ids:
        return []

    sql = text(f"""
        SELECT {_RESULT_COLUMNS}
        FROM public_service_reservations
        WHERE service_id = ANY(:service_ids)
          AND deleted_at IS NULL
    """)

    result = await session.execute(sql, {"service_ids": service_ids})
    keys = result.keys()
    rows = [dict(zip(keys, row)) for row in result.fetchall()]

    # 입력 순서를 보존: dict 인덱싱 후 service_ids 순서대로 재정렬.
    # 원본에 없는 service_id는 자동 제외된다.
    by_id = {r["service_id"]: r for r in rows}
    hydrated = [by_id[sid] for sid in service_ids if sid in by_id]

    # 누락 관측 — 검색(on_ai.service_embeddings)은 물었는데 원본(on_data)에 없어
    # 조용히 사라진 건. 전량 누락이면 사용자에게는 "검색 0건"으로 보이므로,
    # 임베딩 stale(삭제 동기화 누락) 여부를 로그로 추적할 수 있어야 한다.
    if len(hydrated) < len(service_ids):
        missing = [sid for sid in service_ids if sid not in by_id]
        logger.warning(
            "hydration 누락 %d/%d건 — on_data 원본에 없거나 soft-delete 됨"
            "(임베딩 삭제 동기화 누락 의심): %s",
            len(missing),
            len(service_ids),
            missing[:_MISSING_LOG_LIMIT],
        )
    return hydrated
