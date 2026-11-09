package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.repository.MemberRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    @NonNull
    private final MemberRepository members;


    /**
     * FR-AUTH-02 세션 관리·로그아웃, FR-AUTH-03 내 정보·탈퇴
     *
     * TODO(FR-AUTH-03): 내 정보를 조회한다.
     * 1. 입력 검증: 인증된 세션.
     * 2. 권한/소유자 확인: 세션의 users.id 본인만.
     * 3. DB/외부 호출: users를 id로 읽고 oauth_accounts.provider를 조인한다. ensure()로 자동 생성하지 않는다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 행이 없으면 401 또는 시트 회원 오류 코드.
     * 6. 반환: id, email, nickname, role, provider, status.
     */
    public Member me(String username) {
        return members.ensure(username);
    }

    /**
     * FR-AUTH-03 내 정보·탈퇴
     *
     * TODO(FR-AUTH-03): 프로필을 수정한다.
     * 1. 입력 검증: nickname 길이(50자) 등 허용 컬럼만 받는다. email·provider 식별자는 변경하지 않는다.
     * 2. 권한/소유자 확인: 본인 users.id.
     * 3. DB/외부 호출: users.nickname, updated_at을 갱신한다.
     *    notifyEmail은 users에 컬럼이 없다. 스펙 미정: notification_settings.email_enabled로 매핑할지, 요청 필드 자체를 둘지.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 검증 실패 시 시트 코드, 부재 시 404/401.
     * 6. 반환: 갱신된 프로필.
     */
    public Member update(String username, String nickname, Boolean notifyEmail) {
        Member current = members.ensure(username);
        Member updated = current.withProfile(
                nickname == null || nickname.isBlank() ? current.nickname() : nickname,
                notifyEmail == null ? current.notifyEmail() : notifyEmail);
        Member saved = members.save(updated);
        log.info("회원 정보 수정 username={}", username);
        return saved;
    }

    /**
     * FR-AUTH-03 내 정보·탈퇴
     *
     * TODO(FR-AUTH-03): 회원 탈퇴를 처리한다.
     * 1. 입력 검증: 인증된 세션.
     * 2. 권한/소유자 확인: 본인만. 마지막 ADMIN이면 LAST_ADMIN로 거부한다.
     * 3. DB/외부 호출: 현재 세션을 무효화하고 oauth_accounts를 삭제한다. 이용권 잔여는 소멸한다.
     * 4. 상태 전이: 스펙 미정: users.status 변경(소프트 탈퇴) vs 행 삭제, 계약·결제·원장 보존 범위, status 허용 값.
     * 5. 예외/에러코드: LAST_ADMIN.
     * 6. 반환: 204.
     */
    public void withdraw(String username) {
        members.deleteByUsername(username);
        log.info("회원 탈퇴 username={}", username);
    }
}
