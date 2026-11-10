# 안티패턴

하지 말 것과 대안. 배경 규칙은 각 항목이 가리키는 문서를 본다. 핵심 금지는 [AGENTS.md](../../AGENTS.md)에도 있다.

## 계층·구조

1. **하지 말 것:** 컨트롤러에서 repository를 직접 호출한다.
   **대신:** `controller` → `service` → `repository`만 탄다. ([structure.md](structure.md))
2. **하지 말 것:** 뚱뚱한 controller를 만든다.
   **대신:** 컨트롤러는 바인딩·권한 주체 전달·DTO 변환·상태 코드만 담당하고 유스케이스는 서비스에 둔다. ([structure.md](structure.md))
3. **하지 말 것:** 만능 service를 만든다.
   **대신:** 유스케이스 단위로 서비스를 나누고, 여러 기능이 만나면 한 유스케이스가 조합한다. ([structure.md](structure.md))
4. **하지 말 것:** service 간 순환 의존을 만든다.
   **대신:** 의존은 한 방향만 허용하고, 공통 처리는 도메인이나 하위 서비스로 내린다. ([structure.md](structure.md))
5. **하지 말 것:** 필터/AOP에 비즈니스 로직을 숨긴다.
   **대신:** 필터·인터셉터·AOP는 횡단 관심만 두고 `config`에서 등록한다. 업무 규칙은 `domain`·`service`에 둔다. ([structure.md](structure.md))

## DTO·domain

6. **하지 말 것:** domain/엔티티를 직접 반환한다.
   **대신:** `dto/<기능>/`의 Request/Response와 `from(domain)`으로 변환한다. ([structure.md](structure.md))
7. **하지 말 것:** 엔티티에 웹/문서 어노테이션을 붙인다.
   **대신:** 직렬화·OpenAPI 설명은 DTO와 `openapi/api-docs.yml`에만 둔다. ([structure.md](structure.md), [tech.md](tech.md))
8. **하지 말 것:** DTO 하나를 여러 API가 공유한다.
   **대신:** API마다 `<기능><동작>Request` / `<기능><동작>Response`를 둔다. 공통 래퍼만 `dto.common`을 쓴다. ([structure.md](structure.md))
9. **하지 말 것:** `Map<String, Object>`로 요청/응답을 만든다.
   **대신:** 필드를 명시한 record DTO를 쓴다. ([structure.md](structure.md), [conventions.md](conventions.md))

## DB·JPA

10. **하지 말 것:** N+1 조회를 방치한다.
    **대신:** 필요한 데이터를 한 번에 조회하거나 배치로 읽는다. ([tech.md](tech.md))
11. **하지 말 것:** EAGER/무분별 `@OneToMany`를 쓴다.
    **대신:** 기본은 지연 로딩이고, 연관은 실제로 쓰는 것만 매핑한다. ([tech.md](tech.md))
12. **하지 말 것:** 트랜잭션 안에서 외부 API를 호출한다.
    **대신:** 트랜잭션은 DB 작업만 감싸고, 외부 호출은 그 바깥에서 한다. ([tech.md](tech.md))
13. **하지 말 것:** `ddl-auto update`를 쓰거나 적용된 Flyway를 수정한다.
    **대신:** `ddl-auto`는 `validate`이고, 새 파일 `V{n}__설명.sql`만 추가한다. 번호는 팀에 알린다. ([tech.md](tech.md), [workflow.md](workflow.md))
14. **하지 말 것:** Spring AI VectorStore와 자체 테이블을 이중화한다.
    **대신:** 우리 테이블에 native SQL(`ORDER BY embedding <=> ?`)만 쓴다. ([tech.md](tech.md))

## 외부 연동·AI

15. **하지 말 것:** `RestClient` 직접 호출을 여러 곳에 흩뜨린다.
    **대신:** `InferenceClient`, `OcrClient`, `LlmClient`, `PaymentGatewayClient` 뒤로만 호출한다. ([tech.md](tech.md))
16. **하지 말 것:** 타임아웃·재시도·실패처리가 없다.
    **대신:** 클라이언트에 제한 시간과 실패 시 오류 코드·복구를 명시한다. ([tech.md](tech.md))
17. **하지 말 것:** 동기 분석으로 요청을 붙잡아 둔다.
    **대신:** 분석을 비동기로 접수하고 `analysis_id`로 폴링한다. ([tech.md](tech.md))
18. **하지 말 것:** 조항을 단건으로 추론하거나 전체 조항을 Claude에 보낸다.
    **대신:** `/classify`·`/embed`는 배치로 보내고, Claude에는 위험 조항만 병렬로 보낸다. ([tech.md](tech.md))
19. **하지 말 것:** FastAPI에 DB/비즈니스 로직이나 `/similarity`를 둔다.
    **대신:** FastAPI는 `/classify`, `/embed`, `/health`, `/models/reload`만 제공한다. 유사도는 Spring에서 계산한다. ([tech.md](tech.md))
20. **하지 말 것:** 마스킹 전에 원문을 외부로 전송한다.
    **대신:** 분할 직후 마스킹하고, 외부 전송본은 마스킹 텍스트만 쓴다. ([security.md](security.md), [tech.md](tech.md))
21. **하지 말 것:** 임계값을 하드코딩한다.
    **대신:** `RiskPolicy`에 두고 테스트와 스펙 임계값과 맞춘다. ([tech.md](tech.md))

## 보안

22. **하지 말 것:** 디버깅용 `permitAll`/CSRF 예외를 커밋한다.
    **대신:** `SecurityConfig` 완화는 사람의 명시적 승인을 받은 뒤만 한다. ([security.md](security.md))
23. **하지 말 것:** 가짜 로그인·백도어 API를 둔다.
    **대신:** 소셜 로그인과 세션만 쓰고, 제거된 dev login을 되살리지 않는다. ([security.md](security.md))
24. **하지 말 것:** 본문·개인정보·토큰을 로깅한다(SQL 바인딩 포함).
    **대신:** 상태 변경과 예외만 남기고 민감값은 제외한다. ([security.md](security.md), [conventions.md](conventions.md))
25. **하지 말 것:** owner 확인 없이 식별자만 조회한다(IDOR).
    **대신:** `user_id`로 소유자를 확인하고, 타인 리소스는 404로 숨긴다. ([security.md](security.md))
26. **하지 말 것:** 웹훅 서명·멱등을 생략한다.
    **대신:** 서명을 검증하고 같은 이벤트는 한 번만 처리한다. ([security.md](security.md))

## 테스트

27. **하지 말 것:** 테스트에서 실제 외부 API를 호출한다.
    **대신:** 클라이언트를 mock하고 H2 `test` 프로필에서 돌린다. ([testing.md](testing.md))
28. **하지 말 것:** 성공 케이스만 테스트한다.
    **대신:** 권한·부재·검증·외부 실패 등 TC에 있는 사례를 먼저 실패하는 테스트로 작성한다. ([testing.md](testing.md))
29. **하지 말 것:** 테스트 간에 데이터를 공유한다.
    **대신:** 각 테스트가 Fixture Monkey 등으로 자신의 픽스처를 만든다. ([testing.md](testing.md))

## 스펙·협업·AI

30. **하지 말 것:** `스펙 미정`을 지어낸다.
    **대신:** 구현을 멈추고 사람에게 묻거나 TODO를 남긴다. ([product.md](product.md))
31. **하지 말 것:** 스펙시트/`api-docs.yml`/`SecurityConfig` 중 하나만 수정한다.
    **대신:** 세 곳을 함께 맞춘 뒤에 완료로 본다. ([product.md](product.md), [tech.md](tech.md))
32. **하지 말 것:** 이슈 범위 밖을 대량 리팩터·포맷한다.
    **대신:** 요청된 범위만 최소 diff로 바꾼다. ([workflow.md](workflow.md))
33. **하지 말 것:** 의존성을 무단으로 추가하거나 Boot 3 기준 코드를 가져온다.
    **대신:** 의존성 추가는 승인을 받고, 스택은 Boot 4.1.2·Java 21을 따른다. ([workflow.md](workflow.md), [tech.md](tech.md))
34. **하지 말 것:** 구현 후에 `TODO(FR-*)`를 방치한다.
    **대신:** 구현이 끝나면 TODO를 지우거나 남은 일만 남기도록 갱신한다. ([product.md](product.md))
35. **하지 말 것:** 여러 기능을 한 큰 PR에 넣는다.
    **대신:** 한 가지 관심사씩 브랜치와 PR을 나눈다. ([workflow.md](workflow.md))
