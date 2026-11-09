package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.IngestJob;
import ac.mju.contractcheck.repository.IngestJobRepository;
import ac.mju.contractcheck.global.error.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {
    @NonNull
    private final IngestJobRepository jobs;


    /**
     * FR-ADM-01 데이터 적재
     *
     * TODO(FR-ADM-01): 국가법령정보센터에서 법령을 수집한다. 주간 수집도 이 로직을 {@code @Scheduled}로 재사용한다(TC-ADM-009).
     * 1. 입력 검증: target, since. 스펙 미정: since 기본값, 주간 스케줄의 요일·시각.
     * 2. 권한/소유자 확인: ADMIN만. 아니면 403(TC-ADM-010). 비회원 401.
     * 3. DB/외부 호출: ingest_jobs(target, status=RUNNING, started_at) insert.
     *    lawSearch.do / lawService.do로 본문을 받아 청크로 나누고 InferenceClient POST /embed 배치 후
     *    legal_documents(UNIQUE doc_type, source_id), legal_chunks를 upsert한다(멱등, TC-ADM-007).
     *    외부 장애는 TC-ADM-008. 완료 후 필요 시 추론 서버 POST /models/reload(TC-ADM-012는 내부 권한).
     * 4. 상태 전이: RUNNING → 성공 시 완료 상태(processed/failed/finished_at), 실패 시 FAILED.
     * 5. 예외/에러코드: 권한 거부, 외부 장애 시 시트 코드. ingest_jobs.status 허용 값은 스펙 미정(현재 스텁은 ACCEPTED).
     * 6. 반환: job id. 진행은 get()으로 폴링.
     */
    public IngestJob laws(String target, String since) {
        IngestJob saved = jobs.save(new IngestJob(0, "ACCEPTED", 0, 0, 0));
        log.info("법령 적재 접수 jobId={} target={}", saved.id(), target);
        return saved;
    }

    /**
     * FR-ADM-01 데이터 적재
     *
     * TODO(FR-ADM-01): 공정위 시정 사례 CSV를 적재한다(TC-ADM-001).
     * 1. 입력 검증: 멀티파트 CSV 크기·형식(TC-ADM-005). 현재 메서드가 파일을 받지 않으므로 시그니처에 MultipartFile을 추가해야 한다.
     * 2. 권한/소유자 확인: ADMIN만(TC-ADM-010). 스키마 역할은 USER/ADMIN이다. 스펙 미정: TC-ADM-010의 PREMIUM 역할.
     * 3. DB/외부 호출: ingest_jobs insert. after_is_deletion 파싱(TC-ADM-003), 잘못된 행은 failed에 누적하고 건너뛴다(TC-ADM-004).
     *    kftc_cases를 upsert(멱등, TC-ADM-002)하고 /embed로 embedding을 채운다. 재적재 후 검색에 반영(TC-ADM-014).
     * 4. 상태 전이: RUNNING → 완료/FAILED, processed/failed/finished_at 갱신.
     * 5. 예외/에러코드: 파일 오류, 권한 거부.
     * 6. 반환: job id, inserted 건수.
     */
    public IngestJob cases() {
        IngestJob saved = jobs.save(new IngestJob(0, "ACCEPTED", 0, 0, 0));
        log.info("시정 사례 적재 접수 jobId={}", saved.id());
        return saved;
    }

    /**
     * FR-ADM-01 데이터 적재
     *
     * TODO(FR-ADM-01): 적재 작업 상태를 조회한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: ADMIN. 비회원 401(TC-ADM-011), USER면 403.
     * 3. DB/외부 호출: ingest_jobs(status, processed, failed, started_at, finished_at).
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: JOB_NOT_FOUND(404).
     * 6. 반환: 작업 상태 DTO.
     */
    public IngestJob get(long id) {
        return jobs.find(id).orElseThrow(() -> new NotFoundException("JOB_NOT_FOUND", "적재 작업을 찾을 수 없습니다."));
    }
}
