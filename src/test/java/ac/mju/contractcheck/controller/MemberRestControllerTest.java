package ac.mju.contractcheck.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-AUTH-03 내 정보 조회·수정. 회원은 테스트마다 새로 가입시킨다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

    @Test
    @DisplayName("TC-AUTH-013 role 필드를 보내도 무시되고 닉네임만 바뀐다")
    void patchIgnoresRoleField() throws Exception {
        Member member = signUp();
        String username = String.valueOf(member.id());

        mvc.perform(patch("/api/v1/members/me").with(user(username)).with(csrf())
                        .contentType("application/json")
                        .content("{\"nickname\":\"새닉\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉"));

        mvc.perform(get("/api/v1/auth/me").with(user(username)))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("API-MEM-02 닉네임이 50자를 넘으면 400 VALIDATION_ERROR를 일으킨다")
    void patchRejectsTooLongNickname() throws Exception {
        Member member = signUp();

        mvc.perform(patch("/api/v1/members/me").with(user(String.valueOf(member.id()))).with(csrf())
                        .contentType("application/json")
                        .content("{\"nickname\":\"" + "가".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("API-MEM-01 내 정보 조회는 id, email, nickname, provider, createdAt이어야 한다")
    void getReturnsProfile() throws Exception {
        Member member = signUp();

        mvc.perform(get("/api/v1/members/me").with(user(String.valueOf(member.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.email").value(member.email()))
                .andExpect(jsonPath("$.provider").value("kakao"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    private Member signUp() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임"));
    }
}
