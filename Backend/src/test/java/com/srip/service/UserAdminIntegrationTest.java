package com.srip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.repository.StudentRepository;
import com.srip.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin user management.
 *
 * <p>The tests that matter here are the ones about the <em>companion record</em>
 * and about what deletion destroys. Creating a login is easy to get right;
 * remembering that a student's marks hang off the student row and not off the
 * login is the part that would quietly lose a child's history.
 *
 * <p>The H2 database is shared for the JVM, so every test creates its own
 * accounts with unique names and leaves the three seeded demo logins alone -
 * deactivating {@code teacher1} here would break an unrelated test class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdminIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final String NEW_PASSWORD = "Str0ngerPass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository students;

    @Autowired
    private TeacherRepository teachers;

    // -- Access --------------------------------------------------------------

    @Test
    void aTeacherCannotReachUserManagement() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer("teacher1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStudentCannotReachUserManagement() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer("student1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userManagementRefusesAnUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    // -- List ----------------------------------------------------------------

    @Test
    void theListCarriesEachAccountsCompanionRecord() throws Exception {
        JsonNode list = asJson(mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode student = findByUsername(list, "student1");
        assertThat(student.get("admissionNo").asText()).isEqualTo("1001");
        assertThat(student.get("studentId").isNumber()).isTrue();

        JsonNode teacher = findByUsername(list, "teacher1");
        assertThat(teacher.get("staffNo").asText()).isEqualTo("T-100");

        // An admin owns no companion record, so those fields are simply absent -
        // Jackson is configured to omit nulls.
        JsonNode admin = findByUsername(list, "admin");
        assertThat(admin.has("admissionNo")).isFalse();
        assertThat(admin.has("staffNo")).isFalse();
    }

    @Test
    void theListCanBeFilteredByRole() throws Exception {
        JsonNode list = asJson(mockMvc.perform(get("/api/admin/users")
                        .param("role", "TEACHER")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(list).isNotEmpty();
        list.forEach(user -> assertThat(user.get("role").asText()).isEqualTo("TEACHER"));
    }

    @Test
    void theSearchMatchesTheFullNameNotJustTheUsername() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .param("query", "priya")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("teacher1"));
    }

    // -- Create --------------------------------------------------------------

    @Test
    void creatingATeacherAlsoCreatesTheTeacherRecord() throws Exception {
        String username = unique("teach");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Rahul Iyer","role":"TEACHER","staffNo":"%s","department":"Science"}
                """.formatted(username, username, PASSWORD, "T-" + username));

        assertThat(created.get("staffNo").asText()).isEqualTo("T-" + username);
        assertThat(created.get("teacherId").isNumber()).isTrue();

        // The record has to be real, not just echoed back in the response: the
        // teacher dashboard and subject assignment both read it from the table.
        assertThat(teachers.findByStaffNo("T-" + username)).isPresent();

        deleteUser(created.get("id").asLong());
    }

    @Test
    void creatingAStudentForAnUnknownAdmissionNumberCreatesTheStudentRecord() throws Exception {
        String username = unique("stud");
        String admissionNo = "9" + System.nanoTime() % 100000;

        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Nisha Gupta","role":"STUDENT","admissionNo":"%s",
                 "className":"9","section":"B","academicYear":"2025-2026"}
                """.formatted(username, username, PASSWORD, admissionNo));

        assertThat(created.get("admissionNo").asText()).isEqualTo(admissionNo);
        assertThat(created.get("className").asText()).isEqualTo("9");
        assertThat(students.findByAdmissionNo(admissionNo)).isPresent();

        deleteUser(created.get("id").asLong());
    }

    @Test
    void creatingAStudentForAKnownAdmissionNumberLinksToThatChildsRecord() throws Exception {
        // 1004 is seeded by migration V2 with marks against it and no login.
        // A second student row would split that history in two.
        Long existing = students.findByAdmissionNo("1004").orElseThrow().getId();
        String username = unique("link");

        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Ishita Rao","role":"STUDENT","admissionNo":"1004"}
                """.formatted(username, username, PASSWORD));

        assertThat(created.get("studentId").asLong()).isEqualTo(existing);

        // Deleting the login must leave that record, and its marks, behind.
        deleteUser(created.get("id").asLong());
        assertThat(students.findByAdmissionNo("1004")).isPresent();
        assertThat(students.findByAdmissionNo("1004").orElseThrow().getUser()).isNull();
    }

    @Test
    void aStudentAlreadyHoldingALoginCannotBeGivenASecondOne() throws Exception {
        mockMvc.perform(authored(post("/api/admin/users"), """
                        {"username":"%s","email":"dup@school.local","password":"%s",
                         "fullName":"Ayushman Sharma","role":"STUDENT","admissionNo":"1001"}
                        """.formatted(unique("dup"), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void aStudentAccountWithoutAnAdmissionNumberIsRefused() throws Exception {
        mockMvc.perform(authored(post("/api/admin/users"), """
                        {"username":"%s","email":"noadm@school.local","password":"%s",
                         "fullName":"No Admission","role":"STUDENT"}
                        """.formatted(unique("noadm"), PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNewStudentRecordWithoutAClassIsRefusedRatherThanHalfCreated() throws Exception {
        mockMvc.perform(authored(post("/api/admin/users"), """
                        {"username":"%s","email":"noclass@school.local","password":"%s",
                         "fullName":"No Class","role":"STUDENT","admissionNo":"%s"}
                        """.formatted(unique("nocls"), PASSWORD, "8" + System.nanoTime() % 100000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTeacherAccountWithoutAStaffNumberIsRefused() throws Exception {
        mockMvc.perform(authored(post("/api/admin/users"), """
                        {"username":"%s","email":"nostaff@school.local","password":"%s",
                         "fullName":"No Staff No","role":"TEACHER"}
                        """.formatted(unique("nostf"), PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDuplicateUsernameIsRejected() throws Exception {
        mockMvc.perform(authored(post("/api/admin/users"), """
                        {"username":"admin","email":"other@school.local","password":"%s",
                         "fullName":"Impostor","role":"ADMIN"}
                        """.formatted(PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void aShortPasswordIsRejectedByValidation() throws Exception {
        mockMvc.perform(authored(post("/api/admin/users"), """
                        {"username":"%s","email":"short@school.local","password":"abc",
                         "fullName":"Short Pass","role":"ADMIN"}
                        """.formatted(unique("short"))))
                .andExpect(status().isBadRequest());
    }

    // -- Edit ----------------------------------------------------------------

    @Test
    void editingAnAccountUpdatesItsCompanionRecordToo() throws Exception {
        String username = unique("edit");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Old Name","role":"TEACHER","staffNo":"%s","department":"Arts"}
                """.formatted(username, username, PASSWORD, "T-" + username));
        long id = created.get("id").asLong();

        mockMvc.perform(authored(put("/api/admin/users/{id}", id), """
                        {"username":"%s","email":"%s@school.local","fullName":"New Name",
                         "staffNo":"%s","department":"Mathematics"}
                        """.formatted(username, username, "T-" + username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.department").value("Mathematics"));

        // The teacher row carries its own copy of the name, which is what the
        // class lists render - leaving it stale would show two different names
        // for one person.
        assertThat(teachers.findByStaffNo("T-" + username).orElseThrow().getFullName())
                .isEqualTo("New Name");

        deleteUser(id);
    }

    @Test
    void anAdminPasswordResetLetsTheUserInWithTheNewPasswordOnly() throws Exception {
        String username = unique("reset");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Reset Me","role":"ADMIN"}
                """.formatted(username, username, PASSWORD));
        long id = created.get("id").asLong();

        mockMvc.perform(authored(put("/api/admin/users/{id}", id), """
                        {"username":"%s","email":"%s@school.local","fullName":"Reset Me",
                         "password":"%s"}
                        """.formatted(username, username, NEW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(login(username, NEW_PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(login(username, PASSWORD)).andExpect(status().isUnauthorized());

        deleteUser(id);
    }

    @Test
    void anEmptyPasswordOnAnEditLeavesTheExistingOneAlone() throws Exception {
        String username = unique("keep");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Keep Pass","role":"ADMIN"}
                """.formatted(username, username, PASSWORD));
        long id = created.get("id").asLong();

        mockMvc.perform(authored(put("/api/admin/users/{id}", id), """
                        {"username":"%s","email":"%s@school.local","fullName":"Keep Pass",
                         "password":""}
                        """.formatted(username, username)))
                .andExpect(status().isOk());

        mockMvc.perform(login(username, PASSWORD)).andExpect(status().isOk());

        deleteUser(id);
    }

    @Test
    void anEditCannotStealAnotherAccountsUsername() throws Exception {
        String username = unique("steal");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Thief","role":"ADMIN"}
                """.formatted(username, username, PASSWORD));
        long id = created.get("id").asLong();

        mockMvc.perform(authored(put("/api/admin/users/{id}", id), """
                        {"username":"admin","email":"%s@school.local","fullName":"Thief"}
                        """.formatted(username)))
                .andExpect(status().isConflict());

        deleteUser(id);
    }

    // -- Activate / deactivate -----------------------------------------------

    @Test
    void deactivatingAnAccountStopsItSigningIn() throws Exception {
        String username = unique("off");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Switch Off","role":"ADMIN"}
                """.formatted(username, username, PASSWORD));
        long id = created.get("id").asLong();

        mockMvc.perform(login(username, PASSWORD)).andExpect(status().isOk());

        mockMvc.perform(authored(patch("/api/admin/users/{id}/status", id), "{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(login(username, PASSWORD)).andExpect(status().isUnauthorized());

        // And back on again, because "deactivate" has to be reversible to be
        // the safe alternative to deleting.
        mockMvc.perform(authored(patch("/api/admin/users/{id}/status", id), "{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(login(username, PASSWORD)).andExpect(status().isOk());

        deleteUser(id);
    }

    @Test
    void deactivatingAnAccountRetiresTheAccessTokenItAlreadyHolds() throws Exception {
        String username = unique("live");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Live Session","role":"TEACHER","staffNo":"%s"}
                """.formatted(username, username, PASSWORD, "T-" + username));
        long id = created.get("id").asLong();

        String token = accessToken(username);
        mockMvc.perform(get("/api/reference/exams").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(authored(patch("/api/admin/users/{id}/status", id), "{\"enabled\":false}"))
                .andExpect(status().isOk());

        // The token is still signed and unexpired, so only the filter's own
        // `enabled` check can stop it. Without that, a sacked member of staff
        // would keep reading the class dashboard until the token lapsed.
        mockMvc.perform(get("/api/reference/exams").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        deleteUser(id);
    }

    @Test
    void anAdminCannotDeactivateTheirOwnAccount() throws Exception {
        long ownId = findByUsername(allUsers(), "admin").get("id").asLong();

        mockMvc.perform(authored(patch("/api/admin/users/{id}/status", ownId), "{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    // -- Delete --------------------------------------------------------------

    @Test
    void deletingATeacherRemovesTheTeacherRecordWithIt() throws Exception {
        String username = unique("gone");
        JsonNode created = create("""
                {"username":"%s","email":"%s@school.local","password":"%s",
                 "fullName":"Leaving Soon","role":"TEACHER","staffNo":"%s"}
                """.formatted(username, username, PASSWORD, "T-" + username));

        deleteUser(created.get("id").asLong());

        assertThat(teachers.findByStaffNo("T-" + username)).isEmpty();
        mockMvc.perform(get("/api/admin/users/{id}", created.get("id").asLong())
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminCannotDeleteTheirOwnAccount() throws Exception {
        long ownId = findByUsername(allUsers(), "admin").get("id").asLong();

        mockMvc.perform(delete("/api/admin/users/{id}", ownId)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAnAccountThatDoesNotExistIsReportedAsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", 9_999_999L)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound());
    }

    // -- Helpers -------------------------------------------------------------

    /** Unique per run, because the H2 database outlives a single test class. */
    private String unique(String prefix) {
        return prefix + (System.nanoTime() % 1_000_000);
    }

    private JsonNode create(String body) throws Exception {
        return asJson(mockMvc.perform(authored(post("/api/admin/users"), body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void deleteUser(long id) throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", id)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isNoContent());
    }

    private JsonNode allUsers() throws Exception {
        return asJson(mockMvc.perform(get("/api/admin/users")
                .header("Authorization", bearer("admin"))).andReturn());
    }

    private MockHttpServletRequestBuilder authored(MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return request
                .header("Authorization", bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private JsonNode findByUsername(JsonNode list, String username) {
        for (JsonNode user : list) {
            if (username.equals(user.get("username").asText())) {
                return user;
            }
        }
        throw new AssertionError("No user named " + username + " in the list");
    }

    private MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
    }

    private String bearer(String username) throws Exception {
        return "Bearer " + accessToken(username);
    }

    private String accessToken(String username) throws Exception {
        return asJson(mockMvc.perform(login(username, PASSWORD)).andReturn())
                .get("accessToken").asText();
    }

    private JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
