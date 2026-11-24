package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.enums.MemberRole;
import ac.mju.contractcheck.domain.enums.MemberStatus;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** enum 이름은 users 테이블에 저장되는 문자열(role, status)이라 바꾸면 기존 데이터와 어긋난다. */
class MemberValuesTest {

    @Test
    @DisplayName("권한 이름은 스펙의 DB 값(USER·ADMIN)과 같다")
    void roleNamesAreDatabaseValues() {
        assertThat(Arrays.stream(MemberRole.values()).map(Enum::name)).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    @DisplayName("상태 이름은 스펙의 DB 값(ACTIVE·WITHDRAWN)과 같다")
    void statusNamesAreDatabaseValues() {
        assertThat(Arrays.stream(MemberStatus.values()).map(Enum::name)).containsExactlyInAnyOrder("ACTIVE", "WITHDRAWN");
    }
}
