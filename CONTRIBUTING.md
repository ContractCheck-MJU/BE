# 기여 안내

작은 학생 팀이 빠르게 개발하기 위한 사람이다. AI 에이전트는 [AGENTS.md](AGENTS.md)와 [docs/steering/](docs/steering/)을 먼저 읽는다. API·스키마의 근거는 [스펙시트](https://docs.google.com/spreadsheets/d/1wjLgLhIR7CT9lP263eIHSWrxBBgrc9r0b7nUlRxKGbI/edit)다.

## 브랜치 전략 (GitHub Flow)

`main`은 항상 배포 가능한 상태다. 직접 push는 하지 않고, 짧은 브랜치에서 PR로만 합친다.

- 보호 규칙: **PR 필수**, **CI 통과 필수**. 사람 승인(required review)은 두지 않는다.
- 브랜치 이름은 `feat/FR-XXX-00-short-desc`, `fix/...`, `refactor/...`, `docs/...`, `chore/...`다. 이슈·FR 하나당 브랜치 하나다.
- `main`을 자주 rebase하거나 merge해서 최신으로 맞춘다.
- 머지는 **squash merge**다. 머지 후 브랜치는 삭제한다.
- 핫픽스도 같은 방식의 `fix/` 브랜치다.
- 릴리스 태그는 필요하면 `v0.x`를 붙인다.

PR은 작게 유지하고, 가능하면 그날 합친다. 여러 기능을 한 PR에 넣지 않는다.

## 작업 순서

1. GitHub 이슈를 고른다. 이슈는 PM TODO 시트와 맞춰 둔다.
2. 이슈를 자신에게 할당한다.
3. `main`에서 브랜치를 딴다. 예: `git checkout -b feat/FR-ANL-01-classify-batch`
4. 구현한다. [AGENTS.md](AGENTS.md)와 해당 [steering](docs/steering/) 문서, 스펙시트의 FR, 코드의 `TODO(FR-XXX-00):` 주석을 따른다. `스펙 미정:`은 추측하지 않고 PM에게 묻는다.
5. `./gradlew test`를 통과시킨다.
6. 커밋한다. 메시지는 저장소 기존 스타일이다.

   ```
   type: 한국어 현재형 문장
   ```

   - type은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`다. 필요하면 `type(scope):`도 쓴다.
   - 제목은 50자 안쪽, 마침표 없음, 서술어는 `한다`로 끝낸다. 예: `docs: 미구현 기능에 TODO(FR) 로직 설명 주석을 단다`
   - 커밋 하나는 한 가지 변경이다. `.env`, 비밀값, 실제 계약서는 넣지 않는다.
7. 브랜치를 push하고 PR을 연다. Draft여도 된다.
8. AI 리뷰 봇(GitHub Copilot, CodeRabbit, Claude 리뷰 등, 저장소에 설정된 것)이 코멘트를 남기면 작성자가 고친다. 봇을 새로 설치할 필요는 없다.
9. CI(`CI` 워크플로의 `test` 잡, `./gradlew test`)가 초록이면 작성자가 **스스로 squash merge**한다.

사람 승인은 기본 경로가 아니다. 다만 `SecurityConfig`, Flyway 마이그레이션, 결제(토스 웹훅·CSRF 예외 등)처럼 위험한 변경은 머지 전에 PM에게 한 번 알린다. 이 확인은 보호 규칙이 아니라 팀 약속이다.

## PR 설명

템플릿은 [`.github/pull_request_template.md`](.github/pull_request_template.md)다. 짧게 적는다.

- 관련 FR / GitHub 이슈
- 변경 요약
- 체크리스트: 스펙시트, `src/main/resources/openapi/api-docs.yml`, `SecurityConfig` 동기화, AI 리뷰 반영 여부

테스트 결과나 Swagger 스크린샷은 필요할 때만 붙인다.

## Flyway

새 마이그레이션 버전 번호는 파일을 쓰기 전에 팀에 알려 충돌을 막는다. 이미 적용된 `V{n}__*.sql`은 고치지 않는다. 세부는 [docs/steering/tech.md](docs/steering/tech.md)다.

## 비밀값

`.env`, 비밀번호, 토큰, 실제 계약서, 개인정보를 커밋하지 않는다. 설정은 환경변수다. [docs/steering/security.md](docs/steering/security.md)를 본다.

## AI 에이전트

에이전트는 코드를 바꾸기 전에 [AGENTS.md](AGENTS.md)를 읽는다. 사람이 PR의 작성자이고, 리뷰 반영과 머지 책임도 사람에게 있다. 에이전트는 요청 없이 푸시·머지·force push를 하지 않는다. 에이전트 전용 세부는 [docs/steering/workflow.md](docs/steering/workflow.md)다.
