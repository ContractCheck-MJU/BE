package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.dto.auth.MeResponse;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-AUTH-02 세션 관리·로그아웃
 */
@RestController
@RequiredArgsConstructor
public class AuthRestController {

    @NonNull
    private final AuthService auth;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-AUTH-02 세션 관리·로그아웃 */
    @GetMapping("/api/v1/auth/me")
    public MeResponse me(Authentication authentication) {
        Member member = auth.me(currentUser.username(authentication));
        return new MeResponse(member.id(), member.nickname(), member.role(), member.provider());
    }

    /** FR-AUTH-02 세션 관리·로그아웃 */
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
