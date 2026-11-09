package ac.mju.contractcheck.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public String username(Authentication authentication) {
        return authentication.getName();
    }
}
