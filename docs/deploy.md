# 배포 (Coolify)

서버 배포는 Coolify로 한다. 이미지는 GitHub Actions가 만들고, Coolify는 받아서 띄우기만 한다. 서버에서 빌드하지 않는다.

## 흐름

```
main push
  → CI: ./gradlew test
  → CD image 잡: 이미지 빌드 → GHCR push (latest · main · sha-<커밋>)
  → CD deploy 잡: Coolify 배포 웹훅 호출
  → Coolify: ghcr.io/contractcheck-mju/be:main pull → 앱 재기동
```

- 테스트가 실패하면 이미지 빌드와 배포를 하지 않는다.
- Actions → CD → Run workflow(`main`)로 수동 재배포할 수 있다.
- Coolify에 올리는 리소스는 세 개다. 같은 Project·Environment에 둔다.

| 리소스 | 종류 | 외부 공개 |
|---|---|---|
| PostgreSQL | Database (PostgreSQL, pgvector 이미지) | 안 함 |
| SeaweedFS | Docker Compose (계약서 원본 S3 호환 저장소) | 안 함 |
| 앱 | Docker Image (`ghcr.io/contractcheck-mju/be:main`) | 도메인(HTTPS) |

## 0. 서버 확인

```bash
uname -m
```

`x86_64`여야 한다. CD는 amd64 이미지만 만든다. `aarch64`(ARM)라면 CD의 `build-push-action`에 `platforms`를 추가해야 한다.

## 1. 서버에서 GHCR 로그인

`ghcr.io/contractcheck-mju/be`는 비공개 패키지다. Coolify 서버의 Docker가 이미지를 받을 수 있게 한 번 로그인한다.

1. GitHub → Settings → Developer settings → Personal access tokens (classic)에서 `read:packages` 권한만 가진 토큰을 만든다. 조직 패키지를 읽을 수 있는 계정이어야 한다.
2. 서버에 SSH로 접속해 Coolify가 Docker를 다루는 사용자(보통 root)로 로그인한다. 토큰은 셸 기록에 남지 않게 표준 입력으로 넘긴다.

```bash
sudo docker login ghcr.io -u <GitHub 사용자명> --password-stdin
```

토큰을 붙여 넣고 Enter, Ctrl+D를 누른다. `Login Succeeded`가 나오면 된다.

## 2. PostgreSQL

Project → Environment → **+ New** → Databases → **PostgreSQL**

| 항목 | 값 |
|---|---|
| Image | `pgvector/pgvector:pg16` (스키마가 `vector` 확장을 쓴다) |
| Username | `contractcheck` |
| Database | `contractcheck` |
| Password | Coolify가 만든 값. 앱 환경 변수에 그대로 넣는다 |
| Make it publicly available | 끈다 |

시작한 뒤 **Postgres URL (internal)** 에서 호스트(리소스 UUID 형태)를 확인한다. 앱의 `DB_URL`은 다음 형식이다.

```
jdbc:postgresql://<내부 호스트>:5432/contractcheck
```

**Backups** 탭에서 정기 백업을 켠다.

## 3. SeaweedFS (계약서 원본 저장소)

앱은 S3 API(path-style)로 SeaweedFS에 계약서 원본을 올린다.

**+ New** → **Docker Compose** (Empty)에 다음을 넣는다. 이미지 태그는 Docker Hub `chrislusf/seaweedfs`에서 안정 버전을 골라 고정한다.

```yaml
services:
  seaweedfs:
    image: chrislusf/seaweedfs:<버전>
    command: server -dir=/data -s3 -s3.port=8333 -s3.config=/etc/seaweedfs/s3.json
    volumes:
      - seaweedfs-data:/data
volumes:
  seaweedfs-data:
```

**Persistent Storage** → **+ Add** → File Mount로 `/etc/seaweedfs/s3.json`을 만든다. 키는 길고 무작위인 값으로 직접 만든다.

```json
{
  "identities": [
    {
      "name": "contractcheck",
      "credentials": [{ "accessKey": "<ACCESS_KEY>", "secretKey": "<SECRET_KEY>" }],
      "actions": ["Admin", "Read", "Write", "List", "Tagging"]
    }
  ]
}
```

- 도메인과 포트 공개는 하지 않는다. 앱만 내부 네트워크로 접속한다.
- 설정에서 **Connect To Predefined Network**를 켠다. 켜야 다른 리소스(앱)가 이 컨테이너에 접속할 수 있다.
- 배포 후 컨테이너 이름(예: `seaweedfs-<UUID>`)을 확인한다. 앱의 `STORAGE_ENDPOINT`는 `http://<컨테이너 이름>:8333`이다.

버킷을 만든다. Coolify의 해당 컨테이너 **Terminal**에서:

```bash
weed shell
```

```
s3.bucket.create -name contractcheck
exit
```

## 4. 앱

**+ New** → **Docker Image**

| 항목 | 값 |
|---|---|
| Image | `ghcr.io/contractcheck-mju/be:main` |
| Ports Exposes | `8080` |
| Domains | API 도메인 (예: `https://api.<도메인>`). Coolify 프록시가 HTTPS 인증서를 발급한다 |

HTTPS는 필수다. 세션 쿠키(`SESSION`)에 `Secure`가 붙어 http에서는 로그인이 유지되지 않는다.

### 환경 변수

| 변수 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | 테스트 서버는 `dev`(Swagger 켜짐), 실제 운영은 `prod`(Swagger 꺼짐) |
| `DB_URL` | 2단계의 JDBC URL |
| `DB_USERNAME` | `contractcheck` |
| `DB_PASSWORD` | 2단계의 비밀번호 |
| `FRONTEND_BASE_URL` | 프론트 주소. 소셜 로그인 성공·실패 후 이 주소로 돌아간다 |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | 카카오 디벨로퍼스 앱 키 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 네이버 개발자 센터 앱 키 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google Cloud OAuth 클라이언트 |
| `STORAGE_ENDPOINT` | 3단계의 `http://<컨테이너 이름>:8333` |
| `STORAGE_REGION` | `us-east-1` |
| `STORAGE_BUCKET` | `contractcheck` |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | 3단계 `s3.json`의 키 |
| `STORAGE_SSE` | 업로드가 서버 측 암호화 오류로 실패하면 `false` |
| `INFERENCE_CLASSIFY_URL` | 분류 서버 주소. 같은 Coolify의 추론 컨테이너면 `http://contractcheck-inference:8000`(네트워크 별칭) |
| `INFERENCE_EMBED_URL` | 임베딩 서버 주소(BGE-M3를 띄운 별도 PC). 서버에서 닿는 주소여야 한다(예: Tailscale `http://100.x.x.x:8000`) |
| `INFERENCE_API_KEY` | 추론 서버의 `CONTRACTCHECK_INTERNAL_API_KEY`와 같은 값 |
| `INFERENCE_TIMEOUT` | 선택. 추론 호출 제한 시간(기본 `10s`) |
| `ANTHROPIC_API_KEY` | Claude API 키(조항 설명 생성) |
| `LLM_MODEL`, `LLM_EFFORT`, `LLM_TIMEOUT` | 선택. 기본값 `claude-sonnet-5-5`, `low`, `10s` |

소셜 로그인, 저장소, 추론 서버, Claude 값이 비어 있어도 앱은 기동한다. 해당 기능만 실패한다. 임베딩 서버에 닿지 않으면 분석이 FAILED가 되고 이용권은 복구된다.

`LOG_PATH`는 이미지 기본값(`/var/log/contractcheck`)을 쓴다. 파일 로그를 재배포 후에도 남기려면 **Persistent Storage**에 볼륨을 추가하고 대상 경로를 `/var/log/contractcheck`로 둔다.

### Health Check

| 항목 | 값 |
|---|---|
| Path | `/actuator/health` |
| Port | `8080` |
| Start Period | `60`초 이상 (Flyway 마이그레이션과 기동 시간) |

`dev`/`prod`는 `server.forward-headers-strategy=framework`라서, Coolify 프록시 뒤에서도 콜백 주소와 Swagger 서버 주소가 https로 만들어진다.

## 5. 소셜 로그인 콜백 주소

각 제공자 콘솔에 API 도메인 기준 콜백 주소를 등록한다.

| 제공자 | 등록 위치 | 주소 |
|---|---|---|
| 카카오 | 카카오 디벨로퍼스 → 내 애플리케이션 → 카카오 로그인 → Redirect URI | `https://<API 도메인>/login/oauth2/code/kakao` |
| 네이버 | 네이버 개발자 센터 → 내 애플리케이션 → API 설정 → Callback URL | `https://<API 도메인>/login/oauth2/code/naver` |
| 구글 | Google Cloud Console → API 및 서비스 → 사용자 인증 정보 → OAuth 클라이언트 → 승인된 리디렉션 URI | `https://<API 도메인>/login/oauth2/code/google` |

## 6. GitHub Actions와 연결

1. 앱 리소스 → **Webhooks**에서 **Deploy Webhook** URL을 복사한다.
2. Coolify → **Keys & Tokens** → API tokens에서 `deploy` 권한 토큰을 만든다.
3. GitHub 저장소 → Settings → Secrets and variables → Actions에 저장소 시크릿을 추가한다.

| 시크릿 | 값 |
|---|---|
| `COOLIFY_WEBHOOK` | 1의 URL |
| `COOLIFY_TOKEN` | 2의 토큰 |

CD의 `deploy` 잡은 GitHub Environment `production`을 쓴다. 첫 실행 때 자동으로 생기고, 배포 기록은 저장소의 Environments에서 볼 수 있다.

## 7. 첫 배포와 확인

1. Actions → **CD** → Run workflow (branch `main`)
2. Coolify의 앱 **Deployments**에서 진행 상황을 본다.
3. 확인
   - `https://<API 도메인>/actuator/health`가 `{"status":"UP"}`
   - `dev` 프로필이면 `https://<API 도메인>/swagger-ui.html`
   - `https://<API 도메인>/oauth2/authorization/kakao`로 로그인 후 `FRONTEND_BASE_URL`로 돌아오는지
   - 계약서 업로드(`POST /api/v1/contracts`)가 성공하는지

## 롤백

앱 리소스의 이미지 태그를 문제없던 커밋의 `sha-<7자리>`로 바꾸고 Coolify에서 Redeploy한다. 고친 커밋이 `main`에 들어가면 태그를 다시 `main`으로 돌린다. 태그가 `sha-…`로 고정된 동안에는 자동 배포가 새 이미지를 받지 않는다.

DB 마이그레이션은 롤백되지 않는다. 새 Flyway 버전이 적용된 뒤 이전 이미지를 띄우면 `ddl-auto=validate`에서 실패할 수 있다.

## 이전 서버

이전 테스트 서버는 Watchtower로 GHCR의 새 이미지를 받아 반영했다. Watchtower가 계속 돌면 그 서버에도 계속 배포되므로, 새 서버로 옮긴 뒤 이전 서버의 Watchtower와 앱 컨테이너를 정리한다.

## 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 이미지 pull 실패 (`denied`, `unauthorized`) | 1단계 GHCR 로그인, 토큰 만료와 `read:packages` 권한 |
| CD `deploy` 잡이 시크릿 없음으로 실패 | 6단계 시크릿 이름 |
| CD `deploy` 잡이 401 | `COOLIFY_TOKEN` 권한(`deploy`)과 만료 |
| 앱이 DB에 접속하지 못함 | `DB_URL` 호스트가 internal 주소인지 |
| Flyway가 `vector` 확장에서 실패 | DB 이미지가 `pgvector/pgvector:pg16`인지 |
| 업로드가 저장소 접속 오류로 실패 | SeaweedFS의 Connect To Predefined Network, `STORAGE_ENDPOINT` 컨테이너 이름·포트 |
| 업로드가 버킷 없음으로 실패 | 3단계 버킷 생성 |
| 소셜 로그인이 redirect URI 오류 | 5단계 콜백 주소가 API 도메인·https와 정확히 같은지 |
| 로그인 후 바로 로그아웃 상태 | 도메인이 https인지 (`SESSION` 쿠키 `Secure`) |
| Health check 실패로 재시작 반복 | Start Period를 늘리고 Coolify 로그에서 기동 오류 확인 |
