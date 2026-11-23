package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.dto.member.MemberResponse;
import ac.mju.contractcheck.dto.member.UpdateMemberRequest;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-AUTH-03 내 정보·탈퇴
 */
@RestController
@RequiredArgsConstructor
public class MemberRestController {

    @NonNull
    private final MemberService members;
    @NonNull
    private final CurrentUser currentUser;
    @NonNull
    private final CookieSerializer cookieSerializer;

    /** FR-AUTH-03 내 정보·탈퇴 */
    @GetMapping("/api/v1/members/me")
    public MemberResponse me(Authentication authentication) {
        return MemberResponse.from(members.me(currentUser.username(authentication)));
    }

    /** FR-AUTH-03 내 정보·탈퇴 */
    @PatchMapping("/api/v1/members/me")
    public MemberResponse update(Authentication authentication, @Valid @RequestBody UpdateMemberRequest request) {
        Member member = members.update(currentUser.username(authentication), request.nickname(), request.notifyEmail());
        return MemberResponse.from(member);
    }

    /** FR-AUTH-03 내 정보·탈퇴. 204와 함께 SESSION 쿠키를 만료시킨다(API-MEM-03). */
    @DeleteMapping("/api/v1/members/me")
    public ResponseEntity<Void> withdraw(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        members.withdraw(currentUser.username(authentication));
        expireSessionCookie(request, response);
        return ResponseEntity.noContent().build();
    }

    /**
     * SESSION 쿠키를 세션 설정과 같은 속성(Path·HttpOnly·SameSite·Secure)으로 만료시킨다(Max-Age=0).
     * 세션 행은 서비스가 모든 기기 몫을 이미 지웠다. 응답이 나간 뒤 이 세션이 다시 저장되지 않는 것은 테스트로 확인했다.
     */
    private void expireSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, ""));
    }
}
