# 작업 흐름

브랜치, 커밋, PR, CI, 에이전트 행동, 성찰 기록. 사람이 따르는 브랜치·PR·셀프 머지 절차는 [CONTRIBUTING.md](../../CONTRIBUTING.md)다. 여기서는 에이전트가 지키면 되는 것만 적는다. 테스트 실행은 [testing.md](testing.md), Flyway 파일 규칙은 [tech.md](tech.md)를 본다.

## 브랜치와 PR

이름은 `feat|fix|docs|refactor|chore/FR-XXX-00-short`다. 한 이슈·FR에 브랜치·PR 하나다. 여러 기능을 한 PR에 넣지 않는다. ([anti-patterns.md](anti-patterns.md) 35)

`main`에는 직접 푸시하지 않는다. PR을 열고(Draft 가능) CI가 통과하면 사람이 squash merge한다. 사람 승인은 보호 규칙이 아니다. `SecurityConfig`, Flyway, 결제처럼 위험한 변경은 머지 전에 PM에게 알리라고 PR에 적는다.

세부 순서와 템플릿은 [CONTRIBUTING.md](../../CONTRIBUTING.md)와 [`.github/pull_request_template.md`](../../.github/pull_request_template.md)다.

## 커밋

자동 린트는 없다. 커밋 전에 `./gradlew test`가 통과해야 한다.

메시지는 저장소에 있는 형식이다.

```
<type>: <한국어로 왜 바꿨는지>
<type>(<scope>): <한국어로 왜 바꿨는지>
```

- type은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci` 중 하나다.
- scope는 패키지 이름이다. 예: `feat(analysis): 위험 등급 판정 추가`.
- 제목은 50자 안쪽, 마침표 없음. 기존 로그처럼 서술어는 `한다`로 끝내는 것을 따른다. 본문은 한 줄 띄고 이유만 쓴다.
- 커밋 하나는 한 가지 변경이다. 기능과 포맷 수정을 섞지 않는다.
- `.env`, `build/`, `.gradle/`, `.idea/`는 넣지 않는다. 비밀번호와 키도 넣지 않는다.
- 사용자가 커밋을 요청하기 전에는 커밋하지 않는다.

## CI

CI는 `main` push와 pull request에서 `./gradlew test`를 실행한다. 워크플로는 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)이고, 잡 이름은 `test`다.

에이전트는 푸시·머지·이력 재작성(`force push` 등)을 스스로 하지 않는다. `main`에는 force push하지 않는다.

## Flyway 번호

새 마이그레이션 버전 번호를 쓰기 전에 팀에 알려 충돌을 막는다. 파일 형식과 적용된 파일 수정 금지는 [tech.md](tech.md)를 따른다.

## AI 에이전트

- 코드를 바꾸기 전에 [AGENTS.md](../../AGENTS.md), 관련 `TODO(FR-*)`, 스펙시트, 이번 작업에 해당하는 [docs/steering/](./) 문서를 읽는다.
- 변경은 이슈 범위에 맞게 최소로 둔다. 의존성을 임의로 추가하지 않고, 대량 재포맷을 하지 않으며, 스펙에 없는 요구사항을 만들지 않는다. Boot 3 기준 코드를 가져와 쓰지 않는다. ([anti-patterns.md](anti-patterns.md) 32·33)
- `스펙 미정:`과 애매한 점은 추측하지 않는다. PR 설명에 적거나 사람에게 묻는다. ([product.md](product.md))
- 완료라고 말하기 전에 컴파일과 `./gradlew test`를 돌린다. 스펙시트·`api-docs.yml`·`SecurityConfig`가 일치하는지도 확인한다.
- PR의 작성자와 머지 책임은 사람에게 있다. AI 리뷰 봇 코멘트는 사람이 반영하거나 이유를 남긴다.

