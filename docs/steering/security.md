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

구현은 `PiiMaskingService`다. 정규식은 코드가 아니라 `src/main/resources/masking/pii-rules.yml` 한 파일에서만 고친다. 마스킹에 실패하거나 재검사(`verify: true`: 주민번호·이메일·전화번호·계좌번호)에서 패턴이 남으면 원문을 돌려주지 않고 `MaskingException`을 던져 외부 전송을 막는다(fail-closed). 마스킹 건수 로그에는 규칙 id와 개수만 남기고 원문·결과는 남기지 않는다.

- **주소 정책**: `소재지`·`임대목적물`·`목적물의 표시`·`물건지` 라벨 주소는 시·도 + 시·군·구까지만 남기고 상세만 가린다. 당사자 주소나 라벨이 없거나 구분이 불분명한 주소는 fail-safe로 전체를 가린다.
- **법인·중개사 정책**: 사업자등록번호·법인등록번호·중개등록번호와 대표자·중개사 이름만 가리고 상호(사무소 명칭 등)는 유지한다.
- **토큰·한계**: 이름은 고정 토큰 `[이름]`을 사용한다. 정규식 기반이라 호칭 없는 이름이나 시·도·라벨이 모두 없는 주소는 한계가 있으며, OCR 이전(원본 PDF 외부 전송) 단계의 처리는 별도 팀 결정이 필요하다.

## 로그

개인정보와 토큰, 요청 본문, 계약서 원문, SQL 바인딩 값을 로그에 남기지 않는다. `local`의 바인딩 TRACE는 로컬 콘솔 전용이다. ([conventions.md](conventions.md), [logging.md](../logging.md), [anti-patterns.md](anti-patterns.md) 24)
