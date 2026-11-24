package ac.mju.contractcheck.service;

import java.time.Duration;
import java.time.Instant;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 서버가 비동기 작업 도중 죽어 UPLOADED·OCR·ANALYZING에 멈춘 건을 주기적으로 FAILED로 정리한다.
 * 정상 실행 중에는 실행기 안에서 실패가 모두 FAILED로 처리되므로(분석은 이용권 복구 포함) 이 정리는 재시작 뒤에만 의미가 있다.
 *
 * <p>다른 인스턴스가 아직 처리 중인 건을 건드리지 않도록 {@code min-age}보다 오래된 건만 정리한다. 기본 15분은 분석 목표(NFR-02, 60초)와
 * OCR 제한 시간(120초)에 재시도를 더한 값보다 넉넉하다. {@code contractcheck.recovery.enabled=false}이면 등록되지 않는다.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "contractcheck.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StuckJobRecovery {

    private final AnalysisService analyses;
    private final ContractService contracts;
    private final Duration minAge;

    public StuckJobRecovery(
            @NonNull AnalysisService analyses,
            @NonNull ContractService contracts,
            @Value("${contractcheck.recovery.min-age:15m}") Duration minAge) {
        this.analyses = analyses;
        this.contracts = contracts;
        this.minAge = minAge;
    }

    @Scheduled(
            initialDelayString = "${contractcheck.recovery.initial-delay:1m}",
            fixedDelayString = "${contractcheck.recovery.interval:5m}")
    public void recover() {
        recover(Instant.now());
    }

    /** 분석 정리와 계약 정리는 서로 독립이라 한쪽이 실패해도 다른 쪽을 계속한다. */
    void recover(Instant now) {
        Instant before = now.minus(minAge);
        try {
            int count = analyses.failStuck(before);
            if (count > 0) {
                log.info("멈춘 분석 {}건을 FAILED로 정리", count);
            }
        } catch (RuntimeException exception) {
            log.error("멈춘 분석 정리 실패", exception);
        }
        try {
            int count = contracts.failStuck(before);
            if (count > 0) {
                log.info("멈춘 계약서 {}건을 FAILED로 정리", count);
            }
        } catch (RuntimeException exception) {
            log.error("멈춘 계약서 정리 실패", exception);
        }
    }
}
