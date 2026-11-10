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

트랜잭션 안에서 외부 API를 호출하지 않는다. ([anti-patterns.md](anti-patterns.md) 12)

외부로 텍스트를 보내기 전에 개인정보를 마스킹한다. ([security.md](security.md), [anti-patterns.md](anti-patterns.md) 20)

## 분석 파이프라인

분석은 비동기다. `analysis_id`를 즉시 돌려주고 폴링으로 조회한다. 동기 요청-응답으로 분석을 끝내지 않는다. ([anti-patterns.md](anti-patterns.md) 17)

조항은 배치로 `/classify`, `/embed`에 보낸다. 단건 추론 루프를 돌리지 않는다. Claude에는 위험 조항만 병렬로 보낸다. 전체 조항을 Claude에 넣지 않는다. ([anti-patterns.md](anti-patterns.md) 18)

신뢰도·근거 점수 등 임계값은 `RiskPolicy`에 둔다. 코드에 매직 넘버로 하드코딩하지 않는다. ([anti-patterns.md](anti-patterns.md) 21)

## 프로필과 환경변수

- `local`(기본), `dev`, `prod`는 같은 PostgreSQL이다. 접속은 `application.yml`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`만 쓴다. 프로필 파일은 이 데이터소스를 덮어쓰지 않는다. 비밀번호는 YAML에 적지 않는다.
- 테스트만 `test` 프로필의 H2를 쓴다. ([testing.md](testing.md))
- Docker는 `cp .env.example .env` 후 `DB_PASSWORD`를 설정한다. `.env`는 커밋하지 않는다. 이미지는 `pgvector/pgvector:pg16`이다. 명령은 [AGENTS.md](../../AGENTS.md)를 본다.
- 배포는 `docker-compose.prod.yml`과 `.env`의 `DB_PASSWORD`다. 예: `docker compose -f docker-compose.prod.yml up -d`.

## Swagger

API 설명은 `src/main/resources/openapi/api-docs.yml`에만 적는다. 컨트롤러·DTO에 springdoc 애노테이션(`@Tag`, `@Operation`, `@Schema` 등)을 추가하지 않는다. 엔드포인트를 바꾸면 YAML도 함께 수정한다. `OpenApiDocsConfig`가 생성 문서와 YAML의 어긋남을 경고한다.

prod 프로필에서는 Swagger UI와 `/v3/api-docs`를 열지 않는다. ([security.md](security.md))

## Flyway와 JPA

- 스키마 변경은 Flyway만 사용한다. 새 파일은 `src/main/resources/db/migration/V{n}__설명.sql`이다. 이미 적용된 마이그레이션은 고치지 않는다.
- `ddl-auto`는 `validate`다. `update`로 스키마를 맞추지 않는다. 스키마는 pgvector가 필요하다. ([anti-patterns.md](anti-patterns.md) 13)
- 새 버전 번호는 쓰기 전에 팀에 알린다. ([workflow.md](workflow.md))
- 현재 repository는 인메모리 구현이다. JPA·JDBC로 바꿀 때 클래스·메서드 시그니처는 유지한다.
- 연관 로딩은 필요한 것만 조회한다. 기본 EAGER나 무분별한 `@OneToMany`로 N+1을 만들지 않는다. ([anti-patterns.md](anti-patterns.md) 10·11)

## 로깅

콘솔·파일 경로, 롤링, 프로필별 레벨, ECS JSON, 요청 ID, ELK 계획은 [logging.md](../logging.md)다. 비밀값·계약 원문은 남기지 않는다. ([security.md](security.md))

