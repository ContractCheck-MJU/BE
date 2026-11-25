# 로깅

Spring Boot 4.1.2 내장 설정(`logging.*`)과 필요한 경우만 `logback-spring.xml`을 쓴다. logstash-logback-encoder는 추가하지 않는다. 개인정보·토큰·요청 본문·계약서 원문·SQL 바인딩 값은 로그에 남기지 않는다. ([security.md](steering/security.md), [conventions.md](steering/conventions.md))

## 프로필별 레벨

| 프로필 | 앱(`ac.mju.contractcheck`) | 루트·라이브러리 | SQL | 출력 |
|---|---|---|---|---|
| `local` | DEBUG | 기본(INFO) | `org.hibernate.SQL` DEBUG, `org.hibernate.orm.jdbc.bind` TRACE(로컬 콘솔 전용) | 콘솔, 사람이 읽는 패턴. 외부 호출 기록만 파일에도 |
| `dev` | INFO | 루트 INFO. Spring·Hibernate·Tomcat·Hikari 기동 로그도 INFO로 나온다 | 끔 | 콘솔은 local과 같은 패턴, 파일은 ECS JSON |
| `prod` | INFO | 루트 WARN | 끔 | 콘솔+파일, ECS JSON |
| `test` | WARN | 루트 WARN, 주요 라이브러리 WARN | 끔 | 콘솔만 |

`local`의 바인딩 값 로그는 공유 파일이나 dev/prod로 복사하지 않는다.

## 외부 호출 기록

외부 클라이언트 호출은 로거 `ac.mju.contractcheck.external`에 한 줄씩 남는다(`ExternalCallLog`). 대상은 Upstage, 토스, 추론 서버(분류·임베딩), Claude, 저장소, 소셜 로그인(토큰 교환·사용자 정보)이다. 로컬 프로세스인 Apple Vision은 대상이 아니다.

```
외부 호출 target=upstage method=POST url=https://api.upstage.ai/v1/document-digitization status=200 elapsedMs=2496 requestBytes=131021 responseBytes=1843 responseBody={...}
```

- 모든 환경: 대상, 메서드, URL(쿼리·결제 키 제외), 상태, 소요 시간, 크기, 실패하면 예외 이름. 4xx·5xx·실패는 WARN, 나머지는 INFO. 재시도하는 추론 서버는 시도마다 남는다. 헤더는 남기지 않는다.
- `local`·`dev`: 내용을 볼 수 있는 가린 요청·응답 본문. 키·토큰·결제 키 필드와 이름·이메일·계좌 필드는 지우고, 나머지 문자열은 개인정보 마스킹 규칙을 거친다. URL은 호스트까지, 숫자 배열과 파일은 크기만 남긴다. 10만 자를 넘으면 자른다.
- `prod`(다른 프로필과 함께 켜도): 비식별 본문(`ExternalBodyDeidentifier`). JSON 구조와 필드 이름, 상태·코드·모델·쪽 수·금액·토큰 수처럼 정해 둔 값만 남기고 문자열은 길이, 그 밖의 숫자는 `(숫자)`로 남긴다. JSON이 아니면 크기만 남긴다. 남길 필드를 늘릴 때는 개인정보가 들어갈 수 없는지 확인한다.
- 저장소와 소셜 로그인은 어느 환경에서도 본문을 남기지 않는다.
- **`local`·`dev`의 마스킹은 정규식 규칙이라 완전하지 않다.** 그 로그 파일은 계약서 내용이 담긴 것으로 다루고 저장소·메신저에 올리지 않는다.

## 파일이 가는 곳과 롤링

경로 루트는 환경변수 `LOG_PATH`다. 기본값은 `./logs`, 컨테이너 이미지는 `/var/log/contractcheck`다.

- 앱 로그: `$LOG_PATH/contractcheck.log`(dev/prod)
- 외부 호출 기록: `local`은 `$LOG_PATH/contractcheck-external.log`, `dev`/`prod`는 앱 로그에 함께
- 에러 전용(ERROR 이상): `$LOG_PATH/contractcheck-error.log`
- Tomcat 액세스 로그(dev/prod): `$LOG_PATH/access/access.yyyy-MM-dd.log`

롤링은 날짜와 크기(파일당 100MB)를 함께 쓴다. 보관은 30일, 전체 용량 상한은 3GB, 아카이브는 gzip(`.%d{yyyy-MM-dd}.%i.gz`)이다. 로컬은 외부 호출 기록만 파일에 쓰고(그 밖의 로그는 콘솔), 테스트는 파일 Appender를 두지 않는다.

Docker에서 확인하려면 호스트 `./logs`를 컨테이너 `LOG_PATH`에 마운트한다. `docker-compose.prod.yml`은 이 볼륨을 포함한다. 로컬 IntelliJ(8080)와 별도로 띄울 때는 예:

```bash
docker run --name contractcheck-app \
  -p 8081:8080 \
  -v "$PWD/logs:/var/log/contractcheck" \
  -e LOG_PATH=/var/log/contractcheck \
  -e SPRING_PROFILES_ACTIVE=dev \
  ...
```

## 포맷

- `local`/`dev` 콘솔: `HH:mm:ss.SSS LEVEL [requestId] logger.method:line - message`
- `prod` 콘솔과 `dev`/`prod` 파일: Boot 내장 구조화 로그 `logging.structured.format.console`(prod)과 `logging.structured.format.file: ecs`. JSON 한 줄에 `@timestamp`, `log.level`, `message`, `service.name`, `ecs.version`, 스택은 `error.*`가 들어간다.
- MDC `requestId`는 `logging.structured.json.context.include: true`로 JSON에 포함된다. 요청 헤더 `X-Request-Id`가 허용 형식이면 재사용하고, 없으면 `MdcRequestIdFilter`가 UUID를 넣는다. Micrometer Tracing 의존성은 아직 없다. 나중에 브리지를 추가하면 같은 MDC/상관 패턴에 `traceId`/`spanId`를 얹을 수 있다.

액세스 로그 패턴은 `"%m %U"`라서 쿼리스트링(토큰 위험)을 남기지 않는다. 요청 ID는 응답 헤더(`%{X-Request-Id}o`)를 남긴다. 액세스 로그는 텍스트이며 ECS JSON이 아니다. 파일에 바로 보이도록 `server.tomcat.accesslog.buffered`는 false다.

## ELK 계획

1. `prod` 컨테이너는 ECS JSON을 stdout과 `$LOG_PATH/contractcheck.log`에 같이 쓴다. `dev`는 파일만 ECS JSON이고, stdout은 사람이 읽는 패턴이다.
2. Filebeat가 호스트(또는 사이드카)에서 JSON 파일을 읽거나, Docker stdout을 수집한다. 한 줄 = 한 이벤트다.
3. Filebeat는 Elasticsearch에 바로 넣거나 Logstash를 거친다. 필드는 ECS(`@timestamp`, `log.level`, `message`, `service.name`, `service.environment`, `error.stack_trace` 등)이므로 추가 매핑은 최소로 둔다. `requestId`는 context/MDC 필드다.
4. 액세스 로그는 별도 Filebeat input(텍스트)으로 두거나, 앱 JSON만 먼저 파이프라인에 태운다.
5. 대시보드·알림은 `log.level: error`, `service.name: contractcheck`, `requestId`로 묶는다. 로그에 계약 원문이나 비밀값이 없는지 수집 전에 한 번 더 확인한다.
