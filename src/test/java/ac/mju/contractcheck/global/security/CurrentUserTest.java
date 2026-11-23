package ac.mju.contractcheck.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.UserId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    private static UsernamePasswordAuthenticationToken authenticated(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("인증 주체 이름(users.id)을 UserId로 돌려준다")
    void returnsUserId() {
        assertThat(currentUser.userId(authenticated("7"))).isEqualTo(new UserId(7));
    }

    @Test
    @DisplayName("users.id가 아닌 주체는 인증되지 않은 것으로 보고 401로 이어지는 예외를 던진다")
    void nonNumericPrincipalIsUnauthenticated() {
        assertThatThrownBy(() -> currentUser.userId(authenticated("alice")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
