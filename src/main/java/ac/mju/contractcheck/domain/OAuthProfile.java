package ac.mju.contractcheck.domain;

import java.util.Map;

/**
 * 소셜 제공자가 돌려준 사용자 정보에서 가입에 필요한 값만 뽑은 것이다.
 * 속성 구조는 스펙시트 '외부 연동' 탭의 제공자별 user-name-attribute를 따른다.
 * email·nickname은 사용자가 동의하지 않으면 없을 수 있다.
 */
public record OAuthProfile(String provider, String providerUserId, String email, String nickname) {

    public static OAuthProfile from(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> kakao(attributes);
            case "naver" -> naver(attributes);
            case "google" -> google(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 소셜 제공자: " + registrationId);
        };
    }

    private static OAuthProfile kakao(Map<String, Object> attributes) {
        Map<String, Object> account = child(attributes, "kakao_account");
        Map<String, Object> profile = child(account, "profile");
        return new OAuthProfile(
                "kakao",
                required(text(attributes, "id")),
                text(account, "email"),
                text(profile, "nickname"));
    }

    private static OAuthProfile naver(Map<String, Object> attributes) {
        Map<String, Object> response = child(attributes, "response");
        return new OAuthProfile(
                "naver",
                required(text(response, "id")),
                text(response, "email"),
                text(response, "nickname"));
    }

    private static OAuthProfile google(Map<String, Object> attributes) {
        return new OAuthProfile(
                "google",
                required(text(attributes, "sub")),
                text(attributes, "email"),
                text(attributes, "name"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String text(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static String required(String providerUserId) {
        if (providerUserId == null) {
            throw new IllegalArgumentException("제공자 회원 식별자가 없다");
        }
        return providerUserId;
    }
}
