# 제품

서비스 목적과 요구사항 운영. 스택·파이프라인은 [tech.md](tech.md), 주석·이름 형식의 코드 측면은 [conventions.md](conventions.md)를 본다.

## 목적과 사용자

자취방 임대차 계약서의 불공정·위험 조항을 찾아 쉬운 말로 설명하고, 수정 전후 비교와 일정·알림을 돕는다. 주 사용자는 임차인(자취생)이다. 법령·시정 사례 적재는 관리자(ADMIN)만 수행한다.

## 핵심 기능 (FR 그룹)

- **FR-AUTH**: 소셜 로그인, 세션·로그아웃, 내 정보·탈퇴
- **FR-UPL**: 계약서 업로드, OCR, 조항 분할, 개인정보 마스킹
- **FR-ANL**: 조항 위험 분류, 분석 결과 조회
- **FR-CMP**: 수정본 비교, 변경 사항 표시
- **FR-EXP**: 쉬운말 설명, 근거 첨부, 수정 문구 제안
- **FR-MIL**: 일정 자동 추출, 일정 관리, `.ics` 내보내기
- **FR-NOT**: 리마인드 발송, 알림 설정·목록
- **FR-PAY**: 결제 요청, 승인, 웹훅, 내역·취소
- **FR-CRD**: 무료 횟수, 유료 차감
- **FR-PRC**: 법령·시정 사례 근거 검색
- **FR-ADM**: 법령·공정위 사례 적재

개별 API와 인수 조건은 스펙시트를 따른다. 동작 설명은 README다.

## 스펙시트

https://docs.google.com/spreadsheets/d/1wjLgLhIR7CT9lP263eIHSWrxBBgrc9r0b7nUlRxKGbI/edit

API 경로·에러 코드·DB 스키마의 근거는 이 시트다. 스펙시트에 없는 API·에러 코드를 추측해서 넣지 않는다.

## 스펙 미정

주석이나 시트에 `스펙 미정:`이 있으면 값을 지어내지 않는다. 구현을 멈추고 사람에게 확인하거나 TODO를 남긴다. 추측한 내용을 커밋하지 않는다. ([anti-patterns.md](anti-patterns.md) 30)

## FR·TODO 주석

- 컨트롤러·서비스 클래스는 담당 FR을 클래스 주석에 나열한다.
- 공개 메서드에는 `/** FR-XXX-00 기능명 */` Javadoc을 단다.
- 미구현 로직은 `TODO(FR-XXX-00):` 주석의 단계를 따른다. 구현이 끝나면 그 TODO를 지우거나 갱신한다. 구현만 하고 TODO를 남겨 두지 않는다. ([anti-patterns.md](anti-patterns.md) 34)

API를 바꿀 때는 스펙시트, `openapi/api-docs.yml`, `SecurityConfig`를 함께 맞춘다. 하나만 고치지 않는다. ([tech.md](tech.md), [security.md](security.md), [anti-patterns.md](anti-patterns.md) 31)
