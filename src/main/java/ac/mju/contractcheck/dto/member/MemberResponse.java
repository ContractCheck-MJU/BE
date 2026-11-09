package ac.mju.contractcheck.dto.member;

import ac.mju.contractcheck.domain.Member;

public record MemberResponse(long id, String email, String nickname, String provider, String createdAt) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.id(), member.email(), member.nickname(), member.provider(), member.createdAt().toString());
    }
}
