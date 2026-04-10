# 아키텍처

## 시스템 구성

``` mermaid
C4Context
  title 서울 열린 데이터 질의응답 시스템

  Person(user, "사용자", "자연어로 질문을 입력하는 최종 사용자")

  System_Boundary(frontend, "Frontend") {
    System(webapp, "Web App", "자연어 질문 및 답변 시각화")
    System(proxy, "Reverse Proxy", "정적 파일 서빙 및 API 프록시")
  }

  System_Boundary(backend, "Backend") {
    System(api, "API 서비스 (SpringBoot)", "인증, 세션, 비즈니스 로직")
    System(ai, "AI 서비스 (FastAPI)", "멀티에이전트 오케스트레이션")
  }

  System_Boundary(data, "Data") {
    SystemDb(rdb, "RDB", "관계형 데이터베이스")
    SystemDb(redis, "Redis", "세션 / 캐싱")
    SystemDb(vectordb, "Vector DB", "벡터 검색 데이터베이스")
  }

  System_Ext(opendata, "서울 열린 데이터 광장", "메타데이터 / Open API")
  System_Ext(llm, "LLM 서비스", "문장생성 / 임베딩")

  Rel(user, webapp, "질문 입력")
  Rel(webapp, proxy, "요청 전달")
  Rel(proxy, api, "API 요청 프록시")
  Rel(api, ai, "AI 처리 요청")

  Rel(api, rdb, "읽기 / 쓰기")
  Rel(api, redis, "세션 조회 / 저장")
  Rel(ai, redis, "캐시 조회 / 저장")
  Rel(ai, vectordb, "벡터 검색")

  Rel(api, opendata, "Open API 호출")
  Rel(ai, llm, "LLM API 호출")
```

---

## 서비스별 컴포넌트 구성

### AI Service (FastAPI + LangGraph)

```
AI Service
├── routers/
│   └── query.py              # POST /query 진입점, 요청/응답 변환만 담당
├── agents/
│   ├── graph.py               # LangGraph 워크플로우 조립
│   ├── router_agent.py        # LLM 기반 의도 분류 (SEARCH / API / FALLBACK)
│   ├── search_agent.py        # Vector DB 유사도 검색
│   ├── api_agent.py           # 공공 Open API 호출
│   ├── answer_agent.py        # 조회 결과 → 자연어 요약
│   └── fallback_agent.py      # 일상 대화 응답 / 데이터 없음 안내
├── tools/
│   ├── vector_search.py       # 벡터 검색
│   └── public_api.py          # 서울 열린데이터 광장 API 호출
├── llm/
│   ├── client.py              # LLM 서비스 접근 (Claude / GPT API 호출 추상화)
│   ├── generator.py           # 문장 생성 (프롬프트 조립 → LLM 호출 → 텍스트 반환)
│   └── embedder.py            # 텍스트 → 벡터 변환 (임베딩 모델 호출)
├── scripts/
│   └── embed_metadata.py      # 열린데이터 광장 크롤링 → 임베딩 → Vector DB 적재 (배치)
├── schemas/
│   ├── state.py               # AgentState (LangGraph 공유 상태)
│   └── query.py               # QueryRequest / QueryResponse
└── middleware/
    └── metrics.py             # 응답시간 측정
```

### API Service (Spring Boot)

```
API Service
├── controller/
│   ├── AuthController.java      # 로그인 / 로그아웃 / 회원가입 엔드포인트
│   └── QueryController.java     # POST /query — 질의 전달
├── service/
│   ├── AuthService.java         # 인증 비즈니스 로직
│   ├── QueryService.java        # 질의 처리 흐름 조율 (AI 서비스 호출 + 이력 저장)
│   └── HistoryService.java      # 질의/응답 이력 조회
├── domain/
│   ├── User.java                # 사용자 엔티티
│   └── QueryHistory.java        # 질의·응답 이력 엔티티
├── repository/
│   ├── UserRepository.java
│   └── QueryHistoryRepository.java
├── client/
│   └── AiServiceClient.java     # FastAPI AI 서비스 HTTP 호출 (WebClient / RestClient)
├── security/
│   ├── SecurityConfig.java      # Spring Security 설정 (Form Login, 세션 기반)
│   ├── CustomUserDetailsService.java
│   └── SessionAuthFilter.java   # 요청마다 세션 검증
└── common/
    ├── exception/
    │   ├── GlobalExceptionHandler.java  # @ControllerAdvice 에러 처리
    │   └── ErrorCode.java
    └── dto/
        ├── QueryRequest.java
        └── QueryResponse.java
```
