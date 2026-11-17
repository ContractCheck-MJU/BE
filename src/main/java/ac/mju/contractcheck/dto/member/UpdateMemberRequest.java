package ac.mju.contractcheck.dto.member;

import jakarta.validation.constraints.Size;

/** 닉네임은 users.nickname(VARCHAR 50) 길이를 넘을 수 없다. 허용 외 필드(role 등)는 받지 않고 무시한다. */
public record UpdateMemberRequest(@Size(max = 50) String nickname, Boolean notifyEmail) {}
