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

추론 서버(FastAPI)는 `InferenceClient`(`HttpInferenceClient`)로만 부른다. 경로는 `POST /classify`, `POST /embed`이고 `X-Internal-Api-Key`를 붙인다. `/classify`에는 `X-ContractCheck-Mode: research-only`도 붙인다. 분류 모델이 약라벨로 학습한 연구·시연용이라 서버가 이 헤더 없이는 403으로 거절하며, 팀 결정에 따라 이진 분류 결과를 그대로 쓴다. 분류와 임베딩은 주소가 다르다. 운영은 분류가 서버의 추론 컨테이너, 임베딩이 메모리가 넉넉한 별도 PC다. 접속은 `contractcheck.inference.*`(`INFERENCE_CLASSIFY_URL`, `INFERENCE_EMBED_URL`, 둘 다 없으면 `INFERENCE_BASE_URL`, `INFERENCE_API_KEY`)다. 분류는 16개, 임베딩은 64개씩 배치로 보낸다. 분류 서버는 조항 하나를 192토큰 창 최대 16개로 나누고 요청당 창 256개까지만 받아서 16개씩 보내야 긴 조항이 섞여도 한도를 넘지 않는다. 제한 시간 10초(`INFERENCE_TIMEOUT`), 5xx·연결 실패는 최대 3회 시도하며 4xx는 재시도하지 않는다. 실패하면 `InferenceException`이고 API로는 503 `INFERENCE_UNAVAILABLE`이다. 서킷브레이커(Resilience4j)는 의존성 합의 전이라 아직 없다. HTTP/1.1로 고정한다(JDK 기본 h2c 업그레이드를 uvicorn이 400으로 거절한다). 로컬에서는 DATA의 모의 서버(`api.mock_inference`, 분류는 키워드 규칙의 가짜 값·임베딩은 실제 BGE-M3)를 8000번으로 띄운다. 실제 서버와 붙이는 통합 테스트는 `./gradlew inferenceTest`다.

RAG는 Spring AI(`spring-ai-rag`)의 구성요소를 쓴다. 임베딩은 `InferenceEmbeddingModel`(추론 서버 `/embed`를 감싼 `EmbeddingModel`), 근거 검색은 `EvidenceRetriever`(`DocumentRetriever`, `legal_chunks` native SQL)다. Spring AI의 VectorStore와 그 테이블은 쓰지 않고, 스타터(자동 설정)도 넣지 않았다.

Claude는 `LlmClient`(`AnthropicLlmClient`, Anthropic 공식 Java SDK `anthropic-java`)로만 부른다. Spring AI `ChatClient`는 쓰지 않는다. 접속은 `contractcheck.llm.*`(`ANTHROPIC_API_KEY`, 모델 기본 `claude-sonnet-5-5`, effort `low`, 제한 시간 10초, SDK 재시도 1회)다. 응답은 구조화 출력(JSON 스키마)으로 받고, 안전 분류 거절은 서버 측 대체 모델(`fallbacks: default`, 베타)이 받는다. 스키마는 코드에 직접 적는다. SDK의 클래스 기반 구조화 출력은 victools 4(Jackson 2)를 쓰는데 Spring AI가 victools 5(Jackson 3)를 끌어와 실행 중 `NoSuchMethodError`가 난다. 시간 초과는 504 `LLM_TIMEOUT`, 그 밖의 실패는 503 `LLM_UNAVAILABLE`이다(상태 코드는 제안값). 설명(FR-EXP)의 근거는 분석 때 저장한 `clauses.embedding`으로 찾으므로(`EvidenceRetriever.searchByClause`) 설명 시점에는 추론 서버를 부르지 않는다.

트랜잭션 안에서 외부 API를 호출하지 않는다. ([anti-patterns.md](anti-patterns.md) 12)

외부로 텍스트를 보내기 전에 개인정보를 마스킹한다. ([security.md](security.md), [anti-patterns.md](anti-patterns.md) 20)

## 결제(토스페이먼츠)

결제는 `PaymentService`가 맡는다. 주문(`order`)은 서버의 상품 가격으로 `payments`에 READY를 만들고, 승인(`confirm`)은 토스를 부르기 전에 입력·소유자·금액·상태를 확인한 뒤 READY → IN_PROGRESS 조건부 갱신으로 동시 승인 중 한 요청만 통과시킨다. 토스 호출(`PaymentGatewayClient`, 구현 `TossPaymentGatewayClient`)은 트랜잭션 밖이며, 성공하면 한 트랜잭션에서 결제를 DONE으로 저장하고 `CreditService.grantPaidOnce`로 유료 이용권과 GRANT 원장을 남긴다(지갑 낙관적 락 충돌은 트랜잭션을 통째로 다시 시도). 거절은 FAILED(422), 토스 장애·시간 초과는 READY로 되돌려 다시 승인할 수 있다(502·504). 토스를 부른 결과는 성공·거절·오류·무응답 모두 `payment_gateway_logs`(V6)에 호출마다 한 행씩 쌓고, 상태 전이(성공은 이용권 지급까지)와 같은 트랜잭션으로 INSERT한다. 쌓은 행은 고치지 않는다. 응답 원문은 로그 파일이 아니라 이 테이블에 남긴다(`payments.raw`는 승인 성공 응답 하나).

시크릿 키는 환경변수 `TOSS_SECRET_KEY`(테스트는 `test_sk`로 시작)로만 받고, 비어 있으면 클라이언트 빈이 없어 앱은 뜨지만 승인은 502다. 로그에는 주문 번호와 상태 코드만 남기고 시크릿 키·결제 키·응답 본문은 남기지 않는다. 클라이언트 키(`test_ck`)는 화면(결제창)에서만 쓴다. 결제 조회·웹훅 검증·취소(FR-PAY-03·04)는 아직 뼈대다. 스펙 미정: 상품 구성(V5의 초기 상품은 임시 값), `payments.status` 허용 값(`PaymentStatus`는 임시), FAILED·CANCELED 주문 재승인 오류 코드, 승인 시간 제한(15초는 제안값).

## 개인정보 마스킹

`PiiMaskingService`가 `PiiRuleRepository`가 읽은 규칙을 파일에 적힌 순서대로 적용한다. 규칙(정규식·치환 문자)은 `src/main/resources/masking/pii-rules.yml` 한 파일이고, 앱 시작 때 한 번 읽는다. 정규식이 틀렸거나 항목이 빠졌거나 id가 겹치면 시작이 실패한다. `contractcheck.masking.rules-location`으로 다른 파일(`file:/...`)을 가리키면 다시 빌드하지 않고 규칙을 바꿀 수 있다. `verify: true`인 규칙(주민번호·이메일·전화번호·계좌번호)은 마스킹 뒤 한 번 더 검사하고, 남아 있으면 `MaskingException`이다.

대상 범위는 주민·외국인등록번호, 법인·사업자·중개등록·부동산고유·운전면허·여권번호, 이메일, 전화·050X 안심번호, 계좌번호, 특약의 현관 비밀번호·차량번호, 우편번호, 계약서 호칭 뒤 한글·영문 이름(고정 토큰 `[이름]`), 주소·상세호실이다. 법인·중개사는 번호류와 대표자·중개사 이름만 가리고 상호(사무소 명칭 등)는 남긴다. 주소는 `소재지`·`임대목적물`·`목적물의 표시`·`물건지` 라벨이 붙은 경우 시·도 + 시·군·구까지만 남기고 상세(읍/면/로/길/동/번지/호/층)만 가리며, 당사자 주소나 라벨이 없는 주소는 fail-safe로 전체를 가린다.

OCR 원문은 마스킹해 `contracts.masked_text`에 저장한다(`ContractProcessor`). 추론 서버로 나가는 분류·임베딩 요청은 `ClauseRiskService`가 조항을 마스킹한 뒤 보내며, 한 조항이라도 실패하면 아무것도 보내지 않고 분석이 FAILED가 된다(이용권 복구). 저장된 조항(`clauses.text`)은 원문이다. 한계: 호칭 없이 쓰인 이름, 시·도와 라벨이 모두 없는 주소, 비정형 표기는 정규식만으로 가리지 못하며 OCR 이전(원본 PDF) 단계는 마스킹 대상이 아니다. 스펙 미정: Upstage OCR 원본 PDF 처리 방침, 마스킹 실패 오류 코드, 번호류 등 임시 토큰 확정 값, `clauses.text`를 마스킹본으로 덮을지 여부, NER 모델 도입 및 체크섬 오류 번호 처리 여부.

## 분석 파이프라인

업로드가 끝나면 `ContractService`가 `ContractUploaded` 이벤트를 발행하고, `ContractProcessor`가 `ocrExecutor`에서 원본을 읽어 OCR → 조항 분할을 진행하며 `contracts.status`를 `UPLOADED → OCR → SPLIT`으로 옮긴다. 실패하면 `FAILED`와 `error_message`(V3)에 사유를 남긴다. OCR은 `contractcheck.ocr.max-attempts`(3)까지 지수 백오프로 다시 시도하고, 이용권은 이 단계에서 차감하지 않는다. OCR 구현체가 없으면(운영의 `upstage` 미구현) 앱은 뜨지만 처리가 `FAILED`로 끝난다. 테스트는 `contractcheck.ocr.enabled=false`로 처리기를 끈다.

분석은 비동기다. `analysis_id`를 즉시 돌려주고 폴링으로 조회한다. 동기 요청-응답으로 분석을 끝내지 않는다. ([anti-patterns.md](anti-patterns.md) 17)

조항은 배치로 `/classify`, `/embed`에 보낸다. 단건 추론 루프를 돌리지 않는다. Claude에는 위험 조항만 병렬로 보낸다. 전체 조항을 Claude에 넣지 않는다. ([anti-patterns.md](anti-patterns.md) 18)

분석은 `AnalysisService`가 접수(이용권 차감, `ANALYZING`)와 비동기 판정(`analysisExecutor`)을 나눠 처리한다. 접수 뒤 어느 단계든 실패하면 `FAILED`로 바꾸고 같은 분석 ID로 이용권을 한 번만 복구한다. 조항 임베딩(`clauses.embedding`)은 JPA에 매핑하지 않고 `ClauseEmbeddingRepository`가 네이티브 SQL로 쓰며, H2에서는 검증되지 않으므로 `./gradlew postgresTest`로 확인한다. 종합 점수·요약 공식은 스펙 미정이라 `AnalysisSummary`의 임시 규칙이다. 서버가 비동기 작업 중에 죽어 `UPLOADED`·`OCR`·`ANALYZING`에 멈춘 건은 `StuckJobRecovery`(`@Scheduled`, 기본 5분마다, 15분 넘은 건만)가 `FAILED`로 바꾸고 분석은 이용권도 복구한다. 자동 재처리는 하지 않고 사용자가 다시 올리거나 요청한다.

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
- 계약·조항·이용권·회원·분석(`analyses`, `risk_results`)은 JPA로 영속화돼 있고, 나머지는 `repository.memory`의 인메모리 임시 구현이다. JPA·JDBC로 바꿀 때 클래스·메서드 시그니처는 유지하고, 영속화가 끝나면 `repository`로 옮긴다.
- 연관 로딩은 필요한 것만 조회한다. 기본 EAGER나 무분별한 `@OneToMany`로 N+1을 만들지 않는다. ([anti-patterns.md](anti-patterns.md) 10·11)

## 로깅

콘솔·파일 경로, 롤링, 프로필별 레벨, ECS JSON, 요청 ID, ELK 계획은 [logging.md](../logging.md)다. 비밀값·계약 원문은 남기지 않는다. ([security.md](security.md))

