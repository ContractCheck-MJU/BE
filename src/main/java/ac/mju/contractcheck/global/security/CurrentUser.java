package ac.mju.contractcheck.global.security;

import ac.mju.contractcheck.domain.UserId;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 인증 경계. 인증 주체 이름(users.id 문자열)을 {@link UserId}로 바꾸는 곳은 여기 한 군데다. */
@Component
public class CurrentUser {

    /** users.id가 아닌 주체는 인증되지 않은 것으로 보고 401(UNAUTHORIZED)로 이어지는 예외를 던진다. */
    public UserId userId(Authentication authentication) {
        return UserId.parse(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("회원 ID가 아닌 인증 주체"));
    }

    /** 마이그레이션 중에만 쓴다. 모든 호출이 {@link #userId}로 바뀌면 지운다. */
    public String username(Authentication authentication) {
        return authentication.getName();
    }
}
