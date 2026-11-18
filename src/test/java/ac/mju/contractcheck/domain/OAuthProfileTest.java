package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-AUTH-01 제공자별 프로필 추출. 스펙시트 '외부 연동' 탭의 user-name-attribute 기준이다. */
class OAuthProfileTest {

    @Test
    @DisplayName("TC-AUTH-001 카카오는 id와 kakao_account에서 식별자·이메일·닉네임을 꺼낸다")
    void kakaoProfile() {
        Map<String, Object> attributes = Map.of(
                "id", 12345L,
                "kakao_account", Map.of("email", "kakao@example.com", "profile", Map.of("nickname", "카카오닉")));

        OAuthProfile profile = OAuthProfile.from("kakao", attributes);

        assertThat(profile.provider()).isEqualTo("kakao");
        assertThat(profile.providerUserId()).isEqualTo("12345");
        assertThat(profile.email()).isEqualTo("kakao@example.com");
        assertThat(profile.nickname()).isEqualTo("카카오닉");
    }

    @Test
    @DisplayName("TC-AUTH-001 네이버는 response 안의 id·email·nickname을 쓴다")
    void naverProfile() {
        Map<String, Object> attributes = Map.of(
                "resultcode", "00",
                "response", Map.of("id", "naver-id", "email", "naver@example.com", "nickname", "네이버닉"));

        OAuthProfile profile = OAuthProfile.from("naver", attributes);

        assertThat(profile.provider()).isEqualTo("naver");
        assertThat(profile.providerUserId()).isEqualTo("naver-id");
        assertThat(profile.email()).isEqualTo("naver@example.com");
        assertThat(profile.nickname()).isEqualTo("네이버닉");
    }

    @Test
    @DisplayName("TC-AUTH-001 구글은 sub·email·name을 쓴다")
    void googleProfile() {
        Map<String, Object> attributes = Map.of("sub", "google-sub", "email", "google@example.com", "name", "구글닉");

        OAuthProfile profile = OAuthProfile.from("google", attributes);

        assertThat(profile.provider()).isEqualTo("google");
        assertThat(profile.providerUserId()).isEqualTo("google-sub");
        assertThat(profile.email()).isEqualTo("google@example.com");
        assertThat(profile.nickname()).isEqualTo("구글닉");
    }

    @Test
    @DisplayName("이메일·닉네임 동의가 없어도 식별자만 있으면 프로필을 만든다")
    void optionalFieldsMayBeAbsent() {
        OAuthProfile profile = OAuthProfile.from("kakao", Map.of("id", 7L));

        assertThat(profile.providerUserId()).isEqualTo("7");
        assertThat(profile.email()).isNull();
        assertThat(profile.nickname()).isNull();
    }

    @Test
    @DisplayName("TC-AUTH-005 지원하지 않는 provider는 거절")
    void unsupportedProviderIsRejected() {
        assertThatThrownBy(() -> OAuthProfile.from("facebook", Map.of("id", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제공자 식별자가 없으면 거절")
    void missingProviderUserIdIsRejected() {
        assertThatThrownBy(() -> OAuthProfile.from("google", Map.of("email", "a@example.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
