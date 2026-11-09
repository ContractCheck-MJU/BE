package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.dto.auth.CsrfResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-AUTH-04 CSRF 방어
 * GET /api/v1/auth/csrf — CSRF 토큰 발급(SPA 첫 로드·로그인 직후·로그아웃 후 호출).
 */
@RestController
public class CsrfRestController {

    /** FR-AUTH-04 CSRF 방어 */
    @GetMapping("/api/v1/auth/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
}
