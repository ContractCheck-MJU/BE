package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.dto.member.MemberResponse;
import ac.mju.contractcheck.dto.member.UpdateMemberRequest;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.MemberService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    /** FR-AUTH-03 내 정보·탈퇴 */
    @GetMapping("/api/v1/members/me")
    public MemberResponse me(Authentication authentication) {
        return MemberResponse.from(members.me(currentUser.username(authentication)));
    }

    /** FR-AUTH-03 내 정보·탈퇴 */
    @PatchMapping("/api/v1/members/me")
    public MemberResponse update(Authentication authentication, @RequestBody UpdateMemberRequest request) {
        Member member = members.update(currentUser.username(authentication), request.nickname(), request.notifyEmail());
        return MemberResponse.from(member);
    }

    /** FR-AUTH-03 내 정보·탈퇴 */
    @DeleteMapping("/api/v1/members/me")
    public ResponseEntity<Void> withdraw(Authentication authentication) {
        members.withdraw(currentUser.username(authentication));
        return ResponseEntity.noContent().build();
    }
}
