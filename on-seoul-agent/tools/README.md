# tools 모듈

에이전트가 공유하는 데이터 조회 도구 모음입니다. 각 tool은 특정 DB 계정과
쿼리 전략에 특화된 독립 함수로, 에이전트가 직접 SQL을 작성하거나 벡터 연산을
다루지 않아도 되도록 추상화합니다.

**책임 범위:**

- 정형 데이터 조회 (`sql_search.py`)
- 의미 기반 유사도 검색 (`vector_search.py`)
- 위치 기반 반경 검색 (`map_search.py`)

모든 tool은 `AsyncSession`을 호출자가 주입하며, 반환 타입이 다르므로 분리된
파일로 관리합니다.

---

## 모듈 구조

```
tools/
├── sql_search.py     # 카테고리·지역·상태·키워드 필터 → list[dict]
├── vector_search.py  # 임베딩 벡터 코사인 유사도 검색 → list[dict]
└── map_search.py     # earthdistance 반경 검색 → GeoJSON FeatureCollection
```

---

## sql_search.py

`public_service_reservations` 테이블을 파라미터화 SQL로 조회합니다. LLM이
생성한 값을 포함한 모든 필터 값은 bind 파라미터로만 전달하여 SQL Injection을
방지합니다.

### 시그니처

```python
async def sql_search(
    session: AsyncSession,
    *,
    max_class_name: str | None = None,
    area_name: str | None = None,
    service_status: str | None = None,
    keyword: str | None = None,
    top_k: int = TOP_K,          # 기본값: 10
) -> list[dict]:
```

### 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `session` | `AsyncSession` | `on_data_reader` 계정 세션 (SELECT 전용) |
| `max_class_name` | `str \| None` | 대분류 카테고리 (체육시설·문화행사·시설대관·교육·진료) |
| `area_name` | `str \| None` | 서울 자치구 (예: 강남구) |
| `service_status` | `str \| None` | 예약 상태 (접수중·접수예정·마감·대기) |
| `keyword` | `str \| None` | 시설명·장소명 키워드 (`%keyword%` ILIKE 검색) |
| `top_k` | `int` | 최대 반환 건수. 기본값: 10 |

### 반환값

`list[dict]` — 아래 컬럼을 가진 딕셔너리 리스트. 결과가 없으면 빈 리스트.

`service_id`, `service_name`, `max_class_name`, `min_class_name`,
`area_name`, `place_name`, `service_status`, `payment_type`,
`service_url`, `receipt_start_dt`, `receipt_end_dt`,
`service_open_start_dt`, `service_open_end_dt`, `coord_x`, `coord_y`,
`target_info`

### 사용 예

```python
from tools.sql_search import sql_search

rows = await sql_search(
    session,
    max_class_name="체육시설",
    area_name="마포구",
    service_status="접수중",
)
```

---

## vector_search.py

`on_ai.service_embeddings` 테이블에서 쿼리 벡터와 코사인 유사도가 높은 결과를
반환합니다. pre-filter로 카테고리·지역·상태를 WHERE 절에 적용하여 관련 범위
내 유사도 비교 정확도를 높입니다.

### 시그니처

```python
async def vector_search(
    session: AsyncSession,
    query_vector: list[float],
    *,
    max_class_name: str | None = None,
    area_name: str | None = None,
    service_status: str | None = None,
    top_k: int = TOP_K,               # 기본값: 10
    min_similarity: float = MIN_SIMILARITY,  # 기본값: 0.6
) -> list[dict]:
```

### 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `session` | `AsyncSession` | `on_ai_app` 계정 세션 (service_embeddings CRUD 권한) |
| `query_vector` | `list[float]` | 쿼리 임베딩 벡터 (차원: 1536) |
| `max_class_name` | `str \| None` | pre-filter: 대분류 카테고리. None이면 미적용 |
| `area_name` | `str \| None` | pre-filter: 자치구. None이면 미적용 |
| `service_status` | `str \| None` | pre-filter: 예약 상태. None이면 미적용 |
| `top_k` | `int` | 반환할 최대 결과 수. 기본값: 10 |
| `min_similarity` | `float` | 코사인 유사도 하한 (0~1). 기본값: 0.6 |

### 반환값

`list[dict]` — `service_id`, `service_name`, `metadata`, `similarity` 키를
가진 딕셔너리 리스트. 결과가 없으면 빈 리스트.

### 사용 예

```python
from tools.vector_search import vector_search

results = await vector_search(
    session,
    query_vector=[0.1, 0.2, ...],  # 1536차원
    area_name="강남구",
    min_similarity=0.7,
)
```

---

## map_search.py

PostgreSQL `earthdistance` + `cube` 확장을 사용하여 사용자 위치(위도·경도)
기준 반경 내 시설을 거리 오름차순으로 조회하고 GeoJSON FeatureCollection으로
반환합니다.

### 전제 조건

- DB에 `cube` 및 `earthdistance` 확장이 활성화되어 있어야 합니다.
  (`scripts/ddl_indexes_on_data.sql` 참고)
- `coord_x` = 경도(longitude), `coord_y` = 위도(latitude).

### 시그니처

```python
async def map_search(
    session: AsyncSession,
    lat: float,
    lng: float,
    *,
    radius_m: int = DEFAULT_RADIUS_M,  # 기본값: 1000
    top_k: int = TOP_K,                # 기본값: 20
) -> dict:
```

### 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `session` | `AsyncSession` | `on_data_reader` 계정 세션 (SELECT 전용) |
| `lat` | `float` | 기준점 위도. 유효 범위: -90.0 ~ 90.0 |
| `lng` | `float` | 기준점 경도. 유효 범위: -180.0 ~ 180.0 |
| `radius_m` | `int` | 검색 반경 (미터). 기본값: 1000 |
| `top_k` | `int` | 최대 반환 건수. 기본값: 20 |

### 반환값

`dict` — GeoJSON FeatureCollection. 각 Feature의 `geometry.coordinates`는
`[경도, 위도]` 순서(GeoJSON 표준)이며, `properties`에는 시설 정보와
`distance_m`(정수, 미터)이 포함됩니다.

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Point",
        "coordinates": [126.9010, 37.5580]
      },
      "properties": {
        "service_id": "M001",
        "service_name": "마포 체육관",
        "distance_m": 320,
        ...
      }
    }
  ]
}
```

시설이 없으면 `{"type": "FeatureCollection", "features": []}` 를 반환합니다.

### 사용 예

```python
from tools.map_search import map_search

geojson = await map_search(
    session,
    lat=37.5665,
    lng=126.9780,
    radius_m=500,
)
```

---

## DB 세션 라우팅

| Tool | DB | 이유 |
|---|---|---|
| `sql_search` | `on_data` (`data_session`) | `public_service_reservations` 정형 데이터 |
| `vector_search` | `on_ai` (`ai_session`) | `service_embeddings` 벡터 인덱스 |
| `map_search` | `on_data` (`data_session`) | `public_service_reservations` 위치 데이터 |

