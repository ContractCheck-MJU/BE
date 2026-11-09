package ac.mju.contractcheck.service;

import ac.mju.contractcheck.service.ContractService;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.repository.MilestoneRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneService {
    @NonNull
    private final MilestoneRepository milestones;
    @NonNull
    private final ContractService contracts;


    /**
     * FR-MIL-01 일정 자동 추출
     *
     * TODO(FR-MIL-01): 계약 일자로 일정을 생성한다. 이용권은 차감하지 않는다(TC-CRD-009).
     * 1. 입력 검증: contractDate, balanceDate, moveInDate, endDate의 형식과 논리(만기 ≥ 입주 등).
     * 2. 권한/소유자 확인: contracts.user_id. 아니면 CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: 계약일·잔금·입주·만기와 갱신거절 통지기한(만기 6~2개월 전, 주택임대차보호법)을
     *    milestones(user_id, contract_id, type, title, due_date, remind_days INT[], done=false)로 insert한다.
     *    재생성 시 같은 계약의 자동 생성분이 중복되지 않게 한다(TC-MIL-004).
     * 4. 상태 전이: 신규 일정 행 추가.
     * 5. 예외/에러코드: 날짜 검증 실패, CONTRACT_NOT_FOUND(404).
     * 6. 반환: 생성된 목록. 스펙 미정: 수동 추가분 보존 여부, 도메인 단일 remindDays와 컬럼 INT[] 매핑.
     */
    public List<Milestone> generate(String owner, long contractId, String contractDate, String balanceDate, String moveInDate, String endDate) {
        contracts.get(owner, contractId);
        log.info("일정 생성 contractId={} owner={}", contractId, owner);
        return List.of(
                milestones.save(new Milestone(0, owner, contractId, "CONTRACT", "계약일", contractDate, 7, false)),
                milestones.save(new Milestone(0, owner, contractId, "BALANCE", "잔금일", balanceDate, 7, false)),
                milestones.save(new Milestone(0, owner, contractId, "MOVE_IN", "입주일", moveInDate, 1, false)),
                milestones.save(new Milestone(0, owner, contractId, "END", "만기일", endDate, 30, false)));
    }

    /**
     * FR-MIL-02 일정 관리
     *
     * TODO(FR-MIL-02): 내 일정 목록을 조회한다.
     * 1. 입력 검증: 컨트롤러의 from/to를 서비스로 넘겨 due_date 구간을 필터한다. 현재 from/to는 사용되지 않는다.
     * 2. 권한/소유자 확인: milestones.user_id = 현재 사용자.
     * 3. DB/외부 호출: due_date 순, 선택적으로 contract_id 조건.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 없음(빈 목록 허용).
     * 6. 반환: 일정 목록.
     */
    public List<Milestone> list(String owner) {
        return milestones.findByOwner(owner);
    }

    /**
     * FR-MIL-02 일정 관리
     *
     * TODO(FR-MIL-02): 일정을 수동으로 추가한다.
     * 1. 입력 검증: type, title, dueDate, remindDays. 스펙 미정: contractId 없는 추가 허용 여부(TC-MIL-005), type 허용 값.
     * 2. 권한/소유자 확인: contractId가 있으면 해당 계약 소유자. 타인 계약은 404(TC-MIL-006).
     * 3. DB/외부 호출: milestones insert. remind_days는 INT[]로 저장한다.
     * 4. 상태 전이: done=false로 생성.
     * 5. 예외/에러코드: CONTRACT_NOT_FOUND(404), 검증 오류.
     * 6. 반환: 생성된 일정.
     */
    public Milestone create(String owner, long contractId, String type, String title, String dueDate, int remindDays) {
        contracts.get(owner, contractId);
        return milestones.save(new Milestone(0, owner, contractId, type, title, dueDate, remindDays, false));
    }

    /**
     * FR-MIL-02 일정 관리
     *
     * TODO(FR-MIL-02): 일정을 수정한다.
     * 1. 입력 검증: 전달된 필드만 갱신한다.
     * 2. 권한/소유자 확인: milestones.user_id. 아니면 MILESTONE_NOT_FOUND(404).
     * 3. DB/외부 호출: title, due_date, remind_days, done을 갱신한다.
     * 4. 상태 전이: done true/false.
     * 5. 예외/에러코드: MILESTONE_NOT_FOUND(404).
     * 6. 반환: 수정된 일정.
     */
    public Milestone update(String owner, long id, String title, String dueDate, Integer remindDays, Boolean done) {
        Milestone milestone = owned(owner, id);
        return milestones.save(milestone.withUpdate(title, dueDate, remindDays, done));
    }

    /**
     * FR-MIL-02 일정 관리
     *
     * TODO(FR-MIL-02): 일정을 삭제한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: MILESTONE_NOT_FOUND(404).
     * 3. DB/외부 호출: milestones 삭제. 종속 notifications.milestone_id 처리는 FK/정책에 따른다.
     * 4. 상태 전이: 행 제거.
     * 5. 예외/에러코드: MILESTONE_NOT_FOUND(404).
     * 6. 반환: 204. 스펙 미정: 이미 발송된 알림을 남길지 여부.
     */
    public void delete(String owner, long id) {
        owned(owner, id);
        milestones.delete(id);
        log.info("일정 삭제 milestoneId={} owner={}", id, owner);
    }

    /**
     * FR-MIL-03 .ics 내보내기
     *
     * TODO(FR-MIL-03): 계약 일정을 iCalendar로 내보낸다. 현재 수동 문자열 조립을 ical4j로 교체한다.
     * 1. 입력 검증: contractId 필수.
     * 2. 권한/소유자 확인: contracts.user_id. CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: 해당 계약의 milestones를 읽고 VCALENDAR/VEVENT/VALARM을 만든다. TZID=Asia/Seoul.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: CONTRACT_NOT_FOUND(404).
     * 6. 반환: text/calendar 본문.
     */
    public String ics(String owner, long contractId) {
        contracts.get(owner, contractId);
        StringBuilder calendar = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//contractcheck//KR\r\n");
        for (Milestone milestone : milestones.findByContract(owner, contractId)) {
            calendar.append("BEGIN:VEVENT\r\nSUMMARY:").append(milestone.title())
                    .append("\r\nDTSTART;VALUE=DATE:").append(milestone.dueDate() == null ? "" : milestone.dueDate().replace("-", ""))
                    .append("\r\nEND:VEVENT\r\n");
        }
        calendar.append("END:VCALENDAR\r\n");
        return calendar.toString();
    }

    private Milestone owned(String owner, long id) {
        return milestones.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("MILESTONE_NOT_FOUND", "마일스톤을 찾을 수 없습니다."));
    }
}
