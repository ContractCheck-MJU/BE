# 코딩 규칙

이름, 롬복·주입, record, null, Optional, 로그. 패키지 배치와 계층 책임은 [structure.md](structure.md), 민감정보 로그는 [security.md](security.md)를 본다.

## 이름

- `@RestController` 클래스는 `~RestController`로 끝낸다. 예: `ContractRestController`.
- `@RestControllerAdvice`는 컨트롤러가 아니다. `ApiExceptionHandler`처럼 역할 이름을 쓴다.
- 유스케이스 클래스는 `~Service`, 저장소는 `~Repository`로 끝낸다.
- 클래스는 `UpperCamelCase`, 메서드와 변수는 `lowerCamelCase`, 상수는 `UPPER_SNAKE_CASE`, 패키지는 소문자다.
- 줄임말을 만들지 않는다. `id`, `url`만 예외로 두고 단어 안에서는 소문자로 쓴다. 예: `contractId`, `fileUrl`.
- boolean 이름은 상태다. `is`, `has`로 시작할 때는 조회로 읽히게 쓴다. 예: `risky`, `lowConfidence`.
- enum 상수는 스펙 표기를 그대로 쓴다. 예: `HIGH`, `CAUTION`, `SAFE`.
- JSON 필드와 경로 변수는 스펙의 camelCase를 따른다. DB 컬럼만 `snake_case`다.
- 참·거짓 플래그 대신 상태 이름이나 enum을 쓴다. `flag`, `temp`, `data`, `obj` 같은 이름은 쓰지 않는다.

## 빈과 롬복

- 스프링 빈의 의존성은 생성자 주입이다. `private final` 필드와 `@RequiredArgsConstructor`를 쓰고, 생성자를 직접 쓰지 않는다. 필드 `@Autowired`는 쓰지 않는다.
- `@RequiredArgsConstructor`가 만드는 생성자는 하나다. Spring이 그 생성자로 주입하므로 `onConstructor_ = @Autowired`는 붙이지 않는다.
- 생성자를 직접 쓰는 경우는 빈이 아닌 객체를 만들 때, 또는 생성 시점에 의존성으로 값을 계산·검증해야 할 때다.
- 모델과 응답은 자바 `record`다. Jackson이 이 record를 직렬화한다. `@Data`, `@Value`, `@Builder`, `val`, `@SneakyThrows`로 바꾸지 않는다.
- 주입 필드는 `lombok.NonNull`이다. 생성자가 null이면 거절하고, 그 애노테이션이 생성자 인자로 복사된다. IntelliJ는 `lombok.NonNull`을 non-null로 본다.
- `lombok.addNullAnnotations = jetbrains`는 Lombok이 소스에 없는 널 가능성을 만들 때 `org.jetbrains.annotations`를 붙인다. 모델에는 쓰지 않는다.
- 테스트 클래스의 `MockMvc`는 필드 `@Autowired`다. 테스트는 스프링 빈이 아니라 생성자만으로는 주입되지 않는다.

## null

- 메서드는 null을 반환하지 않는다. 값이 없을 수 있는 조회는 `Optional`을 반환하고, 반드시 있어야 하는 유스케이스는 `NotFoundException`을 던진다.
- 컬렉션과 배열은 null 대신 빈 값이다. `Map.of`에 null을 넣지 않는다. 빈 문자열로 바꾸거나 키를 뺀다.
- PATCH에서 null 필드는 그 값을 지우지 않고 그대로 둔다. 필수 값이 없으면 메서드 입구에서 거절한다.
- null과 빈 문자열은 다르다. 공백만 있는 문자열은 `isBlank()`로 거절한다.
- 없는 리소스와 타인 리소스는 둘 다 404다. NPE가 500으로 나가게 두지 않는다. 소유자 확인은 [security.md](security.md)를 본다.

## Optional

`Optional`은 저장소 조회에서 "없을 수 있음"을 표현하는 반환 타입이다.

- 반환하는 곳: `repository`의 단건 조회. 예: `@NonNull Optional<Contract> findOwned(long id, String owner)`.
- 반환 메서드에 `lombok.NonNull`을 붙여 Optional 자체가 null이 아님을 IDE에 알린다. 조회 인자에는 붙이지 않는다. 붙이면 없는 값도 NPE가 되어 500이 된다.
- 구현은 null이면 `Optional.empty()`, 있으면 `Optional.of`다. `Optional.ofNullable`로 끝내지 않는다.
- 반환하지 않는 곳: `controller`, JSON 응답, 레코드 컴포넌트, 필드, 매개변수, 컬렉션 원소. `Optional<List<T>>`도 쓰지 않고 빈 리스트를 반환한다.
- `service`는 `Optional`을 풀어 유스케이스를 끝낸다. 리소스가 필수면 `orElseThrow`로 `NotFoundException`을 던지고, 컨트롤러까지 `Optional`을 올리지 않는다.
- 소유자가 다르면 없는 것과 같게 `Optional.empty()`를 반환한다. 그 결과도 404다.
- `get()`, `isPresent()` 다음에 `get()` 하는 패턴은 쓰지 않는다. 변환은 `map`, 조건은 `filter`, 있을 때와 없을 때는 `ifPresentOrElse`, 기본값은 `orElse`, 필수 값은 `orElseThrow`다.
- `orElse`의 기본값을 만들 비용이 있으면 `orElseGet`을 쓴다. 예외가 필요하면 `orElseThrow`만 쓴다.

## 로그

- 로그는 SLF4J다. 클래스에 `@Slf4j`를 붙이고, 구현은 `spring-boot-starter-logging`(Logback)이다.
- 상태를 바꾸는 유스케이스와 예외 처리에서만 남긴다. 개인정보와 토큰, 요청 본문, SQL 바인딩 값은 남기지 않는다. 상관 ID는 MDC `requestId`다. 경로·포맷·프로필 레벨은 [logging.md](../logging.md), 민감정보는 [security.md](security.md)다.
