# 보안

인증·인가, 비밀값, 마스킹, 웹훅, 로그. SecurityFilterChain 등록 위치는 [structure.md](structure.md), prod Swagger 설정 배경은 [tech.md](tech.md)를 본다.

공개 저장소다.

## 비밀값

비밀, 키, `.env`, 실제 계약서, 개인정보를 커밋하지 않는다. 설정값은 환경변수를 쓴다. `.env`, `build/`, `.gradle/`, `.idea/`는 커밋에 넣지 않는다.

## SecurityConfig

- `SecurityConfig`의 `permitAll`이나 CSRF 예외를 넓히지 않는다. 변경이 필요하면 사람의 명시적 승인을 받는다. 디버깅용 예외를 커밋하지 않는다. ([anti-patterns.md](anti-patterns.md) 22)
- 운영 코드에 개발용·가짜 로그인, 백도어 API를 두지 않는다. 예전의 dev login은 고의로 제거했다. ([anti-patterns.md](anti-patterns.md) 23)
- OAuth 시작·콜백(`/oauth2/authorization`, `/login/oauth2/code`)은 스프링 시큐리티 경로다. 컨트롤러로 만들지 않는다.
- prod 프로필에서는 Swagger UI와 `/v3/api-docs`를 열지 않는다.

API 경로를 바꿀 때는 스펙시트, `openapi/api-docs.yml`, `SecurityConfig`를 함께 맞춘다. ([product.md](product.md), [tech.md](tech.md))

## 소유자와 IDOR

없는 리소스와 타인 리소스는 둘 다 404로 숨긴다. 소유자(`user_id`)를 확인하지 않고 식별자만 믿으면 IDOR가 된다. repository 단건 조회는 소유자가 다르면 `Optional.empty()`를 반환한다. ([conventions.md](conventions.md), [anti-patterns.md](anti-patterns.md) 25)

## 웹훅

토스 웹훅은 서명을 검증하고, 같은 이벤트를 멱등하게 처리한다. 검증 없이 상태를 바꾸거나 횟수를 두 번 지급하지 않는다. ([anti-patterns.md](anti-patterns.md) 26)

## 마스킹

OCR·분할 이후, `/classify`, `/embed`, Claude 등 외부 전송 전에 개인정보를 마스킹한다. 원문을 그대로 외부로 보내지 않는다. ([tech.md](tech.md), [anti-patterns.md](anti-patterns.md) 20)

## 로그

개인정보와 토큰, 요청 본문, 계약서 원문, SQL 바인딩 값을 로그에 남기지 않는다. `local`의 바인딩 TRACE는 로컬 콘솔 전용이다. ([conventions.md](conventions.md), [logging.md](../logging.md), [anti-patterns.md](anti-patterns.md) 24)
