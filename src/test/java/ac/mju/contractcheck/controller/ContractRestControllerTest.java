package ac.mju.contractcheck.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** FR-UPL-01 계약서 업로드 HTTP 계약. 저장소는 mock한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

    @MockitoBean
    StorageClient storage;

    @Test
    @DisplayName("TC-UPL-001 업로드하면 201과 contractId, status=UPLOADED를 응답한다")
    void uploadReturnsCreated() throws Exception {
        String username = newUsername();

        mvc.perform(multipart("/api/v1/contracts")
                        .file(new MockMultipartFile("file", "lease.pdf", "application/pdf", "%PDF-1.7 body".getBytes()))
                        .param("title", "신촌 원룸").param("contractType", "MONTHLY")
                        .with(user(username)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.contractId").isNumber())
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        mvc.perform(get("/api/v1/contracts").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("신촌 원룸"));
    }

    @Test
    @DisplayName("TC-UPL-002 지원하지 않는 형식을 올리면 415 UNSUPPORTED_FILE_TYPE JSON을 응답한다")
    void unsupportedTypeReturns415() throws Exception {
        mvc.perform(multipart("/api/v1/contracts")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {'M', 'Z', 1, 2}))
                        .param("title", "제목").param("contractType", "MONTHLY")
                        .with(user(newUsername())).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("TC-UPL-016 없는 계약이나 타인의 계약을 조회하면 404 CONTRACT_NOT_FOUND를 응답한다")
    void unknownContractReturns404() throws Exception {
        mvc.perform(get("/api/v1/contracts/999999").with(user(newUsername())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    private String newUsername() {
        String providerUserId = UUID.randomUUID().toString();
        return String.valueOf(auth.signIn(
                new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id());
    }
}
