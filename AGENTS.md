# contractcheck 에이전트 핵심 규칙

자취방 계약서 위험 분석 서비스다. Spring Boot 4.1.2 · Java 21 · Spring Security 7 · Jackson 3 · Hibernate 7 · Flyway · PostgreSQL+pgvector · Spring Session JDBC. 추론은 FastAPI(`POST /classify`, `POST /embed`, `GET /health`, `POST /models/reload`만)다. 동작 설명은 README, API·스키마의 근거는 스펙시트다. 상세 규칙은 [docs/steering/](docs/steering/)을 읽는다.

코드를 바꾸기 전에 이 파일과, 아래 색인에서 이번 작업에 해당하는 steering 문서를 읽는다.

## 명령

macOS 터미널의 기본 JDK가 8일 수 있다. JDK 21 이상으로 `JAVA_HOME`을 맞춘다. 없으면 `settings.gradle`의 foojay resolver가 받는다.

```bash
./gradlew test
./gradlew test --tests ac.mju.contractcheck.domain.RiskLevelResolverTest

DB_URL=jdbc:postgresql://localhost:5432/contractcheck \
DB_USERNAME=contractcheck DB_PASSWORD=*** \
./gradlew bootRun                          # local 프로필(기본)

cp .env.example .env                       # DB_PASSWORD를 설정한다. .env는 커밋하지 않는다.
docker compose up -d
```

프로필은 `local`(기본), `dev`, `prod`다. 테스트만 `test` 프로필의 H2를 쓴다.

## 절대 하지 말 것

1. 비밀, 키, `.env`, 실제 계약서, 개인정보를 커밋하지 않는다.
2. `스펙 미정:`을 추측해서 메우지 않는다. 멈추고 사람에게 묻거나 TODO를 남긴다.
3. `SecurityConfig`의 `permitAll`이나 CSRF 예외를 사람 승인 없이 넓히지 않는다.
4. 트랜잭션 안에서 외부 API를 호출하지 않는다.
5. 테스트에서 실제 외부 API를 호출하지 않는다. 클라이언트는 mock한다.
6. 컨트롤러는 repository에 접근하지 않는다. 의존은 `controller` → `service` → `repository`만 허용한다.
7. 에이전트는 푸시·머지·이력 재작성(`force push` 포함)을 스스로 하지 않는다. `main`에는 force push하지 않는다.

상세는 [anti-patterns.md](docs/steering/anti-patterns.md)다.

## 완료 기준

- 컴파일이 통과한다.
- `./gradlew test`가 통과한다.
- API 경로와 동작이 스펙시트, `src/main/resources/openapi/api-docs.yml`, `SecurityConfig`와 일치한다.

## 문서 색인

| 문서 | 언제 읽을지 |
|---|---|
| [product.md](docs/steering/product.md) | 서비스 목적, 사용자, FR, 스펙시트, `스펙 미정`·TODO 주석을 다룰 때 |
| [tech.md](docs/steering/tech.md) | 스택, Spring/FastAPI 분담, 외부 연동, 분석 파이프라인, 프로필, Swagger, Flyway |
| [structure.md](docs/steering/structure.md) | 패키지, 의존 방향, 계층 책임, Filter/Interceptor/AOP, 리소스 위치 |
| [conventions.md](docs/steering/conventions.md) | 롬복·주입, record, 이름, null, Optional, 로그 |
| [testing.md](docs/steering/testing.md) | 테스트를 추가하거나 고칠 때 |
| [security.md](docs/steering/security.md) | 인증·인가, 비밀값, 마스킹, 웹훅, 로그, SecurityConfig |
| [workflow.md](docs/steering/workflow.md) | 브랜치, 커밋, PR, CI, 에이전트 행동 |
| [anti-patterns.md](docs/steering/anti-patterns.md) | 하지 말 것과 대안을 확인할 때 |
| [logging.md](docs/logging.md) | 로그 경로·롤링·포맷·프로필 레벨, ELK 연동 |

Kiro를 쓰는 경우 `.kiro/steering/`에 이 디렉터리를 심볼릭 링크하거나 복사하면 된다. 저장소에는 `.kiro`를 두지 않는다.
