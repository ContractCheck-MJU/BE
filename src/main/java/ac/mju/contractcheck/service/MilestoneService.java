package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.MilestoneRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-MIL-01 일정 자동 추출
 * FR-MIL-02 일정 관리
 * FR-MIL-03 .ics 내보내기
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneService {

    private static final int MAX_TYPE_LENGTH = 30;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final Set<String> AUTO_TYPES = Set.of("CONTRACT", "BALANCE", "MOVE_IN", "RENEWAL_NOTICE", "END");

    @NonNull
    private final MilestoneRepository milestones;
    @NonNull
    private final ContractService contracts;

    /**
     * FR-MIL-01 일정 자동 추출
     * 계약 일자를 검증한 뒤 계약일·잔금일·입주일·갱신거절 통지기한(만기 2개월 전, 주택임대차보호법)·만기일 5건을 생성한다.
     * 이용권은 차감하지 않는다(TC-CRD-009). 재생성 시 같은 계약의 자동 생성 일정만 교체하고 수동 추가 일정은 보존한다(TC-MIL-004).
     */
    @Transactional
    public List<Milestone> generate(
            UserId owner, long contractId, String contractDate, String balanceDate, String moveInDate, String endDate) {
        LocalDate contract = parseRequiredDate(contractDate, "계약일");
        LocalDate balance = parseRequiredDate(balanceDate, "잔금일");
        LocalDate moveIn = parseRequiredDate(moveInDate, "입주일");
        LocalDate end = parseRequiredDate(endDate, "만기일");
        if (balance.isBefore(contract) || moveIn.isBefore(contract) || end.isBefore(moveIn)) {
            throw new ApiException(400, "VALIDATION_ERROR", "일정 날짜 순서가 올바르지 않습니다.");
        }
        contracts.get(owner, contractId);
        milestones.deleteByContractAndTypes(owner, contractId, AUTO_TYPES);
        LocalDate renewalNotice = end.minusMonths(2);
        List<Milestone> created = milestones.saveAll(List.of(
                new Milestone(0, owner, contractId, "CONTRACT", "계약일", contract.toString(), 7, false),
                new Milestone(0, owner, contractId, "BALANCE", "잔금일", balance.toString(), 7, false),
                new Milestone(0, owner, contractId, "MOVE_IN", "입주일", moveIn.toString(), 1, false),
                new Milestone(0, owner, contractId, "RENEWAL_NOTICE", "갱신거절 통지기한", renewalNotice.toString(), 30, false),
                new Milestone(0, owner, contractId, "END", "만기일", end.toString(), 30, false)));
        log.info("일정 생성 contractId={} owner={} count={}", contractId, owner, created.size());
        return created;
    }

    /**
     * FR-MIL-02 일정 관리
     * 내 일정 목록을 기한(due_date) 오름차순으로 조회한다. from·to를 주면 해당 구간으로 필터링한다.
     */
    public List<Milestone> list(UserId owner, String from, String to) {
        LocalDate fromDate = parseOptionalDate(from, "from");
        LocalDate toDate = parseOptionalDate(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(400, "VALIDATION_ERROR", "from은 to보다 늦을 수 없습니다.");
        }
        return milestones.findByOwner(owner, fromDate, toDate);
    }

    /**
     * FR-MIL-02 일정 관리
     * 본인 소유 계약에 수동 일정을 추가한다(contractId 없는 일정은 비허용, 타인 계약은 404 TC-MIL-006).
     */
    @Transactional
    public Milestone create(UserId owner, long contractId, String type, String title, String dueDate, int remindDays) {
        if (contractId <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "contractId는 필수입니다.");
        }
        String validatedType = validateRequiredText(type, MAX_TYPE_LENGTH, "일정 유형");
        String validatedTitle = validateRequiredText(title, MAX_TITLE_LENGTH, "일정 제목");
        LocalDate parsedDueDate = parseRequiredDate(dueDate, "기한");
        validateRemindDays(remindDays);
        contracts.get(owner, contractId);
        Milestone saved = milestones.save(
                new Milestone(0, owner, contractId, validatedType, validatedTitle, parsedDueDate.toString(), remindDays, false));
        log.info("일정 추가 milestoneId={} contractId={} owner={}", saved.id(), contractId, owner);
        return saved;
    }

    /**
     * FR-MIL-02 일정 관리
     * 본인 소유 일정의 전달된 필드(title, dueDate, remindDays, done)만 갱신한다. 없거나 타인 일정이면 404다.
     */
    @Transactional
    public Milestone update(UserId owner, long id, String title, String dueDate, Integer remindDays, Boolean done) {
        Milestone milestone = owned(owner, id);
        String validatedTitle = title == null ? null : validateRequiredText(title, MAX_TITLE_LENGTH, "일정 제목");
        String validatedDueDate = dueDate == null ? null : parseRequiredDate(dueDate, "기한").toString();
        if (remindDays != null) {
            validateRemindDays(remindDays);
        }
        return milestones.save(milestone.withUpdate(validatedTitle, validatedDueDate, remindDays, done));
    }

    /**
     * FR-MIL-02 일정 관리
     * 본인 소유 일정을 삭제한다. 없거나 타인 일정이면 404다.
     * 스펙 미정: 이미 발송된 알림을 남길지 여부(notifications 영속화 시 확정).
     */
    @Transactional
    public void delete(UserId owner, long id) {
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
    public String ics(UserId owner, long contractId) {
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

    private Milestone owned(UserId owner, long id) {
        return milestones.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("MILESTONE_NOT_FOUND", "마일스톤을 찾을 수 없습니다."));
    }

    private static LocalDate parseRequiredDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", field + "은(는) 필수입니다.");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw new ApiException(400, "VALIDATION_ERROR", field + "은(는) YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private static LocalDate parseOptionalDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseRequiredDate(raw, field);
    }

    private static String validateRequiredText(String raw, int maxLength, String field) {
        if (raw == null || raw.isBlank() || raw.trim().length() > maxLength) {
            throw new ApiException(400, "VALIDATION_ERROR", field + "은(는) 1~" + maxLength + "자여야 합니다.");
        }
        return raw.trim();
    }

    private static void validateRemindDays(int remindDays) {
        if (remindDays < 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "리마인드 일수는 0 이상이어야 합니다.");
        }
    }
}
