package com.srip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication and, more importantly, authorisation.
 *
 * <p>Role checks are only half the story in a school system. The tests that
 * matter here are the row-level ones: one student must not be able to read
 * another's marks, and a parent must not be able to read a child who is not
 * theirs. Those cannot be expressed as URL rules, so they are what these tests
 * target.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndAccessControlIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository students;

    @Test
    void loginReturnsBothTokensAndTheCallersProfile() throws Exception {
        mockMvc.perform(login("student1", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                // A student login resolves to a student record, which is what
                // makes the /me endpoints possible.
                .andExpect(jsonPath("$.user.studentId").isNumber());
    }

    @Test
    void theWrongPasswordIsRejectedWithoutSayingWhichHalfWasWrong() throws Exception {
        mockMvc.perform(login("student1", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void anUnknownUsernameGivesTheSameAnswerAsABadPassword() throws Exception {
        mockMvc.perform(login("nobody", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void aProtectedEndpointWithoutATokenReturnsJsonNotALoginRedirect() throws Exception {
        mockMvc.perform(get("/api/analytics/me/results"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void aGarbledTokenIsTreatedAsNoTokenAtAll() throws Exception {
        mockMvc.perform(get("/api/analytics/me/results")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStudentCannotReadAnotherStudentsResults() throws Exception {
        String token = accessToken("student1");
        Long someoneElse = students.findByAdmissionNo("STU1002").orElseThrow().getId();

        mockMvc.perform(get("/api/analytics/students/{id}/results", someoneElse)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStudentCanReadTheirOwnResults() throws Exception {
        String token = accessToken("student1");
        Long own = students.findByAdmissionNo("STU1001").orElseThrow().getId();

        mockMvc.perform(get("/api/analytics/students/{id}/results", own)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aParentCanReadTheirLinkedChildButNotAnotherFamilysChild() throws Exception {
        String token = accessToken("parent1");
        Long linkedChild = students.findByAdmissionNo("STU1001").orElseThrow().getId();
        Long otherChild = students.findByAdmissionNo("STU1003").orElseThrow().getId();

        mockMvc.perform(get("/api/analytics/students/{id}/results", linkedChild)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/analytics/students/{id}/results", otherChild)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeacherCanReadAnyStudentInTheSchool() throws Exception {
        String token = accessToken("teacher1");
        Long anyStudent = students.findByAdmissionNo("STU1003").orElseThrow().getId();

        mockMvc.perform(get("/api/analytics/students/{id}/results", anyStudent)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void classWideAnalyticsAreClosedToStudentsAndParents() throws Exception {
        // A ranking table names every child in the class, so it is staff-only.
        mockMvc.perform(get("/api/analytics/class/10/exams/1/rankings")
                        .header("Authorization", "Bearer " + accessToken("student1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/class/10/exams/1/rankings")
                        .header("Authorization", "Bearer " + accessToken("parent1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyStaffCanUploadResults() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file("file", "admission_no,exam_code\n".getBytes())
                        .header("Authorization", "Bearer " + accessToken("student1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAnAdminCanCreateAccounts() throws Exception {
        String body = """
                {"username":"nope","email":"nope@school.local","password":"Passw0rd!",
                 "fullName":"No One","role":"STUDENT","admissionNo":"STU1004"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + accessToken("teacher1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTheTokenSoTheOldOneCannotBeReplayed() throws Exception {
        JsonNode tokens = asJson(mockMvc.perform(login("student1", PASSWORD))
                .andExpect(status().isOk())
                .andReturn());

        String firstRefresh = tokens.get("refreshToken").asText();

        JsonNode refreshed = asJson(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(firstRefresh);

        // Presenting the spent token again must fail - that is the whole point
        // of rotation.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsIdempotentAndDoesNotLeakWhetherATokenWasKnown() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("a-token-that-was-never-issued")))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutRevokesEveryRefreshTokenForTheAccount() throws Exception {
        JsonNode tokens = asJson(mockMvc.perform(login("student1", PASSWORD)).andReturn());
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aParentCanListTheirOwnChildren() throws Exception {
        mockMvc.perform(get("/api/reference/my-children")
                        .header("Authorization", "Bearer " + accessToken("parent1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].admissionNo").value("STU1001"));
    }

    @Test
    void aStudentCannotListTheClassRoster() throws Exception {
        mockMvc.perform(get("/api/reference/students")
                        .header("Authorization", "Bearer " + accessToken("student1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void referenceExamsAreOpenToAnyAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/reference/exams")
                        .header("Authorization", "Bearer " + accessToken("student1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("UT1-2025"));
    }

    // -- Helpers -------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
    }

    private String accessToken(String username) throws Exception {
        return asJson(mockMvc.perform(login(username, PASSWORD)).andReturn())
                .get("accessToken").asText();
    }

    private String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    private JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
