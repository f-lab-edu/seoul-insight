# AI 서비스 구현 목록

FastAPI + LangGraph 기반 멀티에이전트 서비스 구현 순서.  
각 Phase는 독립적으로 동작 확인 후 다음 단계로 진행한다.

---

## Phase 1. 프로젝트 뼈대

- [ ] 디렉토리 구조 생성 (`routers/`, `agents/`, `tools/`, `llm/`, `schemas/`, `middleware/`, `scripts/`)
- [ ] `main.py` → FastAPI 앱 진입점으로 전환 (`app = FastAPI()`, uvicorn 실행)
- [ ] `pydantic-settings` 기반 설정 관리 (`config.py`, `.env` 로드)
- [ ] 로깅 설정

검증: `uvicorn main:app --reload` 실행 후 `/docs` 접근 확인

---

## Phase 2. 스키마 정의

- [ ] `schemas/query.py` — `QueryRequest` / `QueryResponse`
- [ ] `schemas/state.py` — `AgentState` (LangGraph 노드 간 공유 상태)

---

## Phase 3. LLM 클라이언트

- [ ] `llm/client.py` — LLM 공급자 추상화 (Claude / GPT 전환 가능하도록)
- [ ] `llm/embedder.py` — 텍스트 → 벡터 변환
- [ ] `llm/generator.py` — 프롬프트 조립 → LLM 호출 → 텍스트 반환

검증: 단순 문장 생성 / 임베딩 단위 테스트

---

## Phase 4. Agent 구현

의존 순서: `router → (search / api / fallback) → answer`

- [ ] `agents/router_agent.py` — LLM 기반 의도 분류 (`SEARCH` / `API` / `FALLBACK`)
- [ ] `agents/fallback_agent.py` — 일상 대화 / 데이터 없음 안내
- [ ] `agents/search_agent.py` — Vector DB 유사도 검색 결과 반환
- [ ] `agents/api_agent.py` — 공공 Open API 호출 결과 반환
- [ ] `agents/answer_agent.py` — 조회 결과 → 자연어 요약

---

## Phase 5. LangGraph 워크플로우

- [ ] `agents/graph.py` — 노드 등록 및 엣지 연결
- [ ] `router_agent` 결과에 따른 조건 분기 (`SEARCH` / `API` / `FALLBACK`)
- [ ] 워크플로우 단독 실행 테스트 (FastAPI 없이 `graph.invoke({...})` 확인)

---

## Phase 6. Tools

- [ ] `tools/public_api.py` — 서울 열린데이터 광장 Open API 호출 (httpx/aiohttp)
- [ ] `tools/vector_search.py` — pgvector 유사도 검색 쿼리

---

## Phase 7. API 엔드포인트

- [ ] `routers/query.py` — `POST /query` (요청 수신 → graph.invoke → 응답 반환)
- [ ] 라우터를 `main.py`에 등록

검증: Swagger UI에서 질의 요청 → 자연어 응답 확인 (MVP 핵심 흐름 완성)

---

## Phase 8. 인프라 연동

- [ ] Redis 연결 설정 및 캐시 조회/저장 (agent 응답 캐싱)
- [ ] PostgreSQL + pgvector 연결 (SQLAlchemy async)
- [ ] `scripts/embed_metadata.py` — 열린데이터 광장 메타데이터 크롤링 → 임베딩 → Vector DB 적재 (배치)

---

## Phase 9. 미들웨어 / 관측

- [ ] `middleware/metrics.py` — 요청별 응답시간 측정
- [ ] 에이전트별 오류율 로깅

---

## Phase 10. 테스트

- [ ] 각 Agent 단위 테스트 (`pytest-asyncio`)
- [ ] graph 통합 테스트 (의도별 분기 시나리오)
- [ ] `/query` 엔드포인트 E2E 테스트

---

## 참고

- LLM 공급자 미확정 → `llm/client.py` 추상화 레이어에서 공급자 교체 가능하도록 설계
- 관리 UI 없음 → Swagger UI (`/docs`) / Postman으로 대체
- 모니터링(Prometheus, Grafana) 연계는 Phase 9 이후 별도 문서화
