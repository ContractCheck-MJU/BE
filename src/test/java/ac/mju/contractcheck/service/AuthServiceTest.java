package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import com.navercorp.fixturemonkey.FixtureMonkey;
import java.util.List;
import net.jqwik.api.Arbitraries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** FR-AUTH-01 소셜 로그인 가입·재로그인, TC-CRD-001 무료 2회 지급. 테스트마다 새 제공자 계정을 만든다. */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    private final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
            .defaultNotNull(true)
            .build();

    @Autowired
    AuthService auth;

    @Autowired
    CreditService credits;

    @Test
    @DisplayName("TC-AUTH-001·TC-CRD-001 최초 로그인은 USER 회원과 무료 2회 지갑·GRANT 원장 1건을 만든다")
    void firstSignInCreatesMemberAndWallet() {
        OAuthProfile profile = profile("kakao");

        Member member = auth.signIn(profile);

        assertThat(member.id().value()).isPositive();
        assertThat(member.role()).isEqualTo("USER");
        assertThat(member.provider()).isEqualTo("kakao");
        assertThat(member.email()).isEqualTo(profile.email());
        assertThat(member.nickname()).isEqualTo(profile.nickname());
        UserId owner = member.id();
        CreditAccount account = credits.me(owner);
        assertThat(account.freeRemaining()).isEqualTo(2);
        assertThat(account.paidRemaining()).isZero();
        List<LedgerEntry> ledger = credits.ledger(owner);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).type()).isEqualTo("GRANT");
        assertThat(ledger.get(0).amount()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-AUTH-002 같은 제공자 계정으로 다시 로그인하면 회원을 새로 만들지 않고 무료 이용권도 다시 주지 않는다")
    void repeatedSignInKeepsMemberAndDoesNotGrantAgain() {
        OAuthProfile profile = profile("naver");

        Member first = auth.signIn(profile);
        Member second = auth.signIn(profile);

        assertThat(second.id()).isEqualTo(first.id());
        UserId owner = first.id();
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        assertThat(credits.ledger(owner)).hasSize(1);
    }

    @Test
    @DisplayName("서로 다른 제공자 계정은 서로 다른 회원이다")
    void differentAccountsAreDifferentMembers() {
        Member first = auth.signIn(profile("google"));
        Member second = auth.signIn(profile("google"));

        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("같은 이메일을 다른 제공자로 가입하려 하면 무결성 예외로 거절하고 회원을 늘리지 않는다 (계정 연결은 스펙 미정)")
    void sameEmailWithOtherProviderIsRejected() {
        OAuthProfile kakao = profile("kakao");
        OAuthProfile google = new OAuthProfile("google", text(), kakao.email(), text());
        auth.signIn(kakao);

        assertThatThrownBy(() -> auth.signIn(google)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private OAuthProfile profile(String provider) {
        String providerUserId = text();
        return new OAuthProfile(provider, providerUserId, providerUserId + "@example.com", text());
    }

    private String text() {
        return fixtureMonkey.giveMeBuilder(String.class)
                .set("$", Arbitraries.strings().alpha().ofLength(12))
                .sample();
    }
}
