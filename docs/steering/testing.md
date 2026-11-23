# 테스트

테스트 작성과 실행. 주입 방식은 [conventions.md](conventions.md), 클라이언트 경계는 [tech.md](tech.md), CI는 [workflow.md](workflow.md)를 본다.

## 원칙

- 새 동작은 스펙시트 TC 하나당 실패하는 테스트 하나에서 시작한다. 테스트 이름에 TC ID를 넣는다. 구현이 테스트를 앞서지 않는다.
- 성공 케이스만 쓰지 않는다. 권한 거부, 없는 리소스, 검증 실패, 외부 장애도 커버한다. ([anti-patterns.md](anti-patterns.md) 28)
- 테스트 사이에 데이터를 공유하지 않는다. 각 테스트가 자신의 픽스처를 만든다. ([anti-patterns.md](anti-patterns.md) 29)
- 픽스처는 Fixture Monkey를 쓴다.

## 웹·보안 테스트

- `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`와 spring-security-test를 쓴다.
- 인증이 필요하면 `@WithMockUser`, 변경 요청에는 `.with(csrf())`를 붙인다.
- 테스트 클래스의 `MockMvc`는 필드 `@Autowired`다. ([conventions.md](conventions.md))

## 외부 연동

외부 클라이언트(`InferenceClient`, `OcrClient`, `LlmClient`, `PaymentGatewayClient`)는 mock한다. 테스트에서 실제 외부 API를 호출하지 않는다. ([anti-patterns.md](anti-patterns.md) 27)

예외로, 이 컴퓨터에서 도는 `AppleVisionOcrClient`(외부 서비스가 아님)는 `client.mac.AppleVisionOcrClientTest`가 실제 Vision으로 검증한다. macOS가 아니거나 `swiftc`가 없으면 자동으로 건너뛰므로 Linux CI에서는 돌지 않는다. macOS에서는 처음 실행 때 헬퍼 컴파일과 모델 로딩으로 1분 가까이 걸린다. 서비스 테스트는 `OcrClient`를 mock한다.

운영 코드에 개발용·가짜 로그인 엔드포인트를 두지 않는다. 예전의 dev login은 고의로 제거했다. ([security.md](security.md))

## 실행 환경

- 테스트는 `src/test/resources/application-test.yml`의 H2(PostgreSQL 모드, Flyway 꺼짐, `ddl-auto=create-drop`)에서 돌린다. 외부 PostgreSQL이 필요 없다.
- 명령: `./gradlew test`. 한 클래스만 돌릴 때는 `./gradlew test --tests ...`다. ([AGENTS.md](../../AGENTS.md))
- 실제 서버가 필요한 테스트는 태그로 `test`에서 빼고 전용 태스크로만 돌린다: `storageTest`(SeaweedFS), `inferenceTest`(추론 서버+Postgres), `postgresTest`(Flyway 마이그레이션, 로컬 Postgres). 모두 CI에서는 돌지 않는다.
- PR 전에 `./gradlew test`가 통과해야 한다. CI(`main` push·PR)가 이를 강제한다.
