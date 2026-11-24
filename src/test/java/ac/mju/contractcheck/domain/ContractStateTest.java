package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractStateTest {

    @Test
    @DisplayName("TC-UPL-006 진행률은 UPLOADED → OCR → SPLIT 순서로 단조 증가한다")
    void progressIncreasesAlongTheFlow() {
        assertThat(ContractState.UPLOADED.progress()).isLessThan(ContractState.OCR.progress());
        assertThat(ContractState.OCR.progress()).isLessThan(ContractState.SPLIT.progress());
        assertThat(ContractState.SPLIT.progress()).isEqualTo(100);
    }

    @Test
    @DisplayName("저장되는 상태 이름은 스펙의 contracts.status 값과 같다")
    void namesMatchSpec() {
        assertThat(Arrays.stream(ContractState.values()).map(Enum::name)).containsExactly("UPLOADED", "OCR", "SPLIT", "FAILED");
    }

    @Test
    @DisplayName("알 수 없는 상태 문자열(분석 단계 값 등)의 진행률은 100이다")
    void unknownStatusIsComplete() {
        assertThat(ContractState.progressOf("ANALYZING")).isEqualTo(100);
        assertThat(ContractState.progressOf("UPLOADED")).isEqualTo(ContractState.UPLOADED.progress());
    }
}
