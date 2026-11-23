# 기술

스택과 연동, 실행 프로필, 스키마·문서 도구. 패키지 배치는 [structure.md](structure.md), 비밀값·인가 경로는 [security.md](security.md), Flyway 번호 공지는 [workflow.md](workflow.md)를 본다.

## 스택

Spring Boot 4.1.2, Java 21, Spring Security 7, Jackson 3, Hibernate 7, Flyway, PostgreSQL + pgvector, Spring Session JDBC. Boot 3이나 Java 17 기준 코드를 가져와 쓰지 않는다. ([anti-patterns.md](anti-patterns.md) 33)

## Spring과 FastAPI

Spring이 DB, 인증, OCR(Upstage), Claude, Toss 결제를 소유한다.

추론 서버는 FastAPI다. 허용 경로는 `POST /classify`, `POST /embed`, `GET /health`, `POST /models/reload`뿐이다. FastAPI에 DB나 비즈니스 로직을 두지 않고, `/similarity`는 쓰지 않는다. ([anti-patterns.md](anti-patterns.md) 19)

벡터 검색은 우리 테이블에 대한 native SQL이다. `ORDER BY embedding <=> ?`를 쓴다. Spring AI `VectorStore` 테이블과 자체 테이블을 이중으로 두지 않는다. ([anti-patterns.md](anti-patterns.md) 14)

## 외부 연동

외부 API는 서비스당 클라이언트 인터페이스 하나(`InferenceClient`, `OcrClient`, `LlmClient`, `PaymentGatewayClient`)를 통해서만 호출한다. `RestClient`를 여러 서비스에 흩뜨리지 않는다. 설정은 YAML과 환경변수다. 타임아웃·재시도·실패 처리를 빠뜨리지 않는다. 테스트에서는 이 클라이언트를 mock한다. ([testing.md](testing.md), [anti-patterns.md](anti-patterns.md) 15·16)

OCR 구현체는 `contractcheck.ocr.provider`(환경변수 `OCR_PROVIDER`)로 고른다. 서비스는 `OcrClient`만 부르고, 구현체는 `@ConditionalOnProperty`로 등록한다. 지금은 `apple-vision`(macOS 로컬 시연용, `client.mac.AppleVisionOcrClient`)뿐이고 기본값이다. 운영은 Linux라 Apple Vision을 쓸 수 없어 prod 프로필 기본값을 `upstage`로 두었다. Upstage 구현체(스펙: Upstage Document Parse)는 아직 없다. 추가할 때는 `OcrClient`를 구현하고 `@ConditionalOnProperty(..., havingValue = "upstage")`만 달면 서비스 코드는 바꾸지 않는다. Apple Vision 구현체는 Xcode Command Line Tools(`swiftc`)가 필요하고, 처음 쓸 때 `ocr/apple-vision/main.swift`를 한 번 컴파일한다.

OS나 로컬 환경에서만 되는 구현체는 `client` 아래 하위 패키지로 묶는다. macOS 전용은 `client.mac`이다. 공통 인터페이스·예외는 `client`에 둔다.

추론 서버(FastAPI)는 `InferenceClient`(`HttpInferenceClient`)로만 부른다. 경로는 `POST /classify`, `POST /embed`이고 `X-Internal-Api-Key`를 붙인다. 접속은 `contractcheck.inference.*`(`INFERENCE_BASE_URL`, `INFERENCE_API_KEY`)다. 64개씩 배치로 보내고, 제한 시간 10초, 5xx·연결 실패는 최대 3회 시도하며 4xx는 재시도하지 않는다. 실패하면 `InferenceException`이고 API로는 503 `INFERENCE_UNAVAILABLE`이다. 서킷브레이커(Resilience4j)는 의존성 합의 전이라 아직 없다. HTTP/1.1로 고정한다(JDK 기본 h2c 업그레이드를 uvicorn이 400으로 거절한다). 로컬에서는 DATA의 모의 서버(`api.mock_inference`, 분류는 키워드 규칙의 가짜 값·임베딩은 실제 BGE-M3)를 8000번으로 띄운다. 실제 서버와 붙이는 통합 테스트는 `./gradlew inferenceTest`다.

RAG는 Spring AI(`spring-ai-rag`)의 구성요소를 쓴다. 임베딩은 `InferenceEmbeddingModel`(추론 서버 `/embed`를 감싼 `EmbeddingModel`), 근거 검색은 `EvidenceRetriever`(`DocumentRetriever`, `legal_chunks` native SQL)다. Spring AI의 VectorStore와 그 테이블은 쓰지 않고, 스타터(자동 설정)도 넣지 않았다. Claude 호출(`ChatClient`)은 FR-EXP-01에서 `EvidenceRetriever`를 어드바이저에 꽂아 붙인다.

트랜잭션 안에서 외부 API를 호출하지 않는다. ([anti-patterns.md](anti-patterns.md) 12)

외부로 텍스트를 보내기 전에 개인정보를 마스킹한다. ([security.md](security.md), [anti-patterns.md](anti-patterns.md) 20)

## 분석 파이프라인

분석은 비동기다. `analysis_id`를 즉시 돌려주고 폴링으로 조회한다. 동기 요청-응답으로 분석을 끝내지 않는다. ([anti-patterns.md](anti-patterns.md) 17)

조항은 배치로 `/classify`, `/embed`에 보낸다. 단건 추론 루프를 돌리지 않는다. Claude에는 위험 조항만 병렬로 보낸다. 전체 조항을 Claude에 넣지 않는다. ([anti-patterns.md](anti-patterns.md) 18)

신뢰도·근거 점수 등 임계값은 `RiskPolicy`에 둔다. 코드에 매직 넘버로 하드코딩하지 않는다. ([anti-patterns.md](anti-patterns.md) 21)

## RAG 데이터 적재

법령·판례 임베딩(`DATA/law-embedding`, `DATA/precedent-search`)은 `scripts/ingest.sh`로 `legal_documents`·`legal_chunks`에 적재한다. 로컬 Postgres를 띄운 뒤 BE 디렉터리에서 실행하고, DATA 파일이 바뀌면 같은 명령을 다시 돌린다. 멱등이라 바뀐 문서만 갱신하고 나머지는 건너뛴다. 옵션은 `--source laws|precedents`, `--dry-run`(검증만), `--force`(변경 없어도 다시 씀)다. 실행마다 `ingest_jobs`에 이력이 남고, 문서 하나가 실패해도 나머지는 계속하며 종료 코드가 1이 된다. 새 소스는 `scripts/ingest_rag.py`의 `SOURCES`에 추가한다. `kftc_cases`는 DATA에 데이터가 없어 아직 소스가 없다. 이 스크립트는 FR-ADM-01의 관리자 적재 API가 생기기 전의 운영 수단이다.

## 프로필과 환경변수

- `local`(기본), `dev`, `prod`는 같은 PostgreSQL이다. 접속은 `application.yml`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`만 쓴다. 프로필 파일은 이 데이터소스를 덮어쓰지 않는다. 비밀번호는 YAML에 적지 않는다.
- 테스트만 `test` 프로필의 H2를 쓴다. ([testing.md](testing.md))
- Docker는 `cp .env.example .env` 후 `DB_PASSWORD`를 설정한다. `.env`는 커밋하지 않는다. 이미지는 `pgvector/pgvector:pg16`이다. 명령은 [AGENTS.md](../../AGENTS.md)를 본다.
- 서버 배포는 Coolify다. CD가 GHCR 이미지를 올리고 Coolify 웹훅으로 재배포한다. 절차와 환경 변수는 [deploy.md](../deploy.md)다. `docker-compose.prod.yml`은 Coolify 없이 한 서버에 수동으로 띄울 때만 쓴다.

## Swagger

API 설명은 `src/main/resources/openapi/api-docs.yml`에만 적는다. 컨트롤러·DTO에 springdoc 애노테이션(`@Tag`, `@Operation`, `@Schema` 등)을 추가하지 않는다. 엔드포인트를 바꾸면 YAML도 함께 수정한다. `OpenApiDocsConfig`가 생성 문서와 YAML의 어긋남을 경고한다.

prod 프로필에서는 Swagger UI와 `/v3/api-docs`를 열지 않는다. ([security.md](security.md))

## Flyway와 JPA

- 스키마 변경은 Flyway만 사용한다. 새 파일은 `src/main/resources/db/migration/V{n}__설명.sql`이다. 이미 적용된 마이그레이션은 고치지 않는다.
- `ddl-auto`는 `validate`다. `update`로 스키마를 맞추지 않는다. 스키마는 pgvector가 필요하다. ([anti-patterns.md](anti-patterns.md) 13)
- 새 버전 번호는 쓰기 전에 팀에 알린다. ([workflow.md](workflow.md))
- 계약을 지우면 조항·분석·위험 결과·설명·비교는 DB가 `ON DELETE CASCADE`로 함께 지우고 일정은 계약 연결만 끊는다(`V2`). 앱 코드에 하위 테이블 삭제를 추가하지 않는다. 회원(`users`)을 가리키는 FK는 바꾸지 않았고, 탈퇴는 원본 파일 삭제 순서 때문에 서비스가 조율한다. 이 규칙은 H2에서 검증되지 않으므로 `./gradlew postgresTest`(로컬 Postgres의 임시 스키마에 Flyway를 적용)로 확인한다.
- 계약·조항·이용권·회원은 JPA로 영속화돼 있고, 나머지는 `repository.memory`의 인메모리 임시 구현이다. JPA·JDBC로 바꿀 때 클래스·메서드 시그니처는 유지하고, 영속화가 끝나면 `repository`로 옮긴다.
- 연관 로딩은 필요한 것만 조회한다. 기본 EAGER나 무분별한 `@OneToMany`로 N+1을 만들지 않는다. ([anti-patterns.md](anti-patterns.md) 10·11)

## 로깅

콘솔·파일 경로, 롤링, 프로필별 레벨, ECS JSON, 요청 ID, ELK 계획은 [logging.md](../logging.md)다. 비밀값·계약 원문은 남기지 않는다. ([security.md](security.md))

