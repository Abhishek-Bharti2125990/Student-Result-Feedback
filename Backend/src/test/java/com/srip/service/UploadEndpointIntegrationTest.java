package com.srip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.domain.UploadJob;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.repository.AiFeedbackRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentAnalyticsRepository;
import com.srip.repository.TopicScoreRepository;
import com.srip.repository.UploadErrorRepository;
import com.srip.repository.UploadJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the upload endpoint the way a client does: multipart POST, then poll.
 *
 * <p>This goes through {@link ResultUploadService#accept} and the real
 * asynchronous job launcher, rather than calling a launcher directly. That
 * distinction matters - launching a Spring Batch job from inside a caller's
 * transaction fails at runtime, and only a test that uses the actual request
 * path can catch it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadEndpointIntegrationTest {

    private static final String HEADER =
            "student_id,student_name,class_name,section,exam_name,subject,chapter_name,topic_name,marks_obtained,maximum_marks\n";

    /** Every row of the file is an item, so the write count is the row count. */
    private static final int TOPIC_ROWS = 192;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResultUploadService uploadService;

    @Autowired
    private ExamResultRepository examResults;

    @Autowired
    private TopicScoreRepository topicScores;

    @Autowired
    private StudentAnalyticsRepository storedAnalytics;

    @Autowired
    private UploadErrorRepository uploadErrors;

    @Autowired
    private AiFeedbackRepository aiFeedback;

    @Autowired
    private UploadJobRepository uploadJobs;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void reset() {
        // Child rows first: the foreign keys are real.
        topicScores.deleteAllInBatch();
        storedAnalytics.deleteAllInBatch();
        examResults.deleteAllInBatch();
        uploadErrors.deleteAllInBatch();
        aiFeedback.deleteAllInBatch();
        uploadJobs.deleteAllInBatch();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void anAdminUploadIsAcceptedAndTheJobRunsToCompletion() throws Exception {
        Long uploadJobId = upload("/api/admin/upload", adminToken(),
                "samples/results-unit-test-1.csv", "results-unit-test-1.csv");

        UploadJobView finished = awaitTerminalStatus(uploadJobId);

        assertThat(finished.status()).isEqualTo(UploadJob.Status.COMPLETED);
        assertThat(finished.validRecords()).isEqualTo(TOPIC_ROWS);
        assertThat(finished.invalidRecords()).isZero();
        assertThat(finished.jobExecutionId()).isNotNull();
        assertThat(finished.completedAt()).isNotNull();
    }

    @Test
    void theAdminUploadStatusEndpointReportsTheSameJob() throws Exception {
        Long uploadJobId = upload("/api/admin/upload", adminToken(),
                "samples/results-unit-test-1.csv", "one.csv");
        awaitTerminalStatus(uploadJobId);

        mockMvc.perform(get("/api/admin/upload/{id}", uploadJobId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.validRecords").value(TOPIC_ROWS));
    }

    @Test
    void aFileWithBadRowsCompletesWithAPerLineErrorReport() throws Exception {
        Long uploadJobId = upload("/api/admin/upload", adminToken(),
                "samples/results-with-errors.csv", "results-with-errors.csv");

        UploadJobView finished = awaitTerminalStatus(uploadJobId);

        assertThat(finished.status()).isEqualTo(UploadJob.Status.COMPLETED_WITH_ERRORS);
        assertThat(finished.validRecords()).isEqualTo(2);
        assertThat(finished.invalidRecords()).isEqualTo(10);
        assertThat(finished.errors()).hasSize(10);
        assertThat(finished.errors()).allSatisfy(error ->
                assertThat(error.lineNumber()).isPositive());
    }

    @Test
    void teachersMayStillUseTheGenericUploadRoute() throws Exception {
        // /api/admin/upload is the documented route, but result ingestion is a
        // staff action and a teacher must not be locked out of it.
        Long uploadJobId = upload("/api/uploads", teacherToken(),
                "samples/results-unit-test-1.csv", "teacher-upload.csv");

        assertThat(awaitTerminalStatus(uploadJobId).status()).isEqualTo(UploadJob.Status.COMPLETED);
    }

    @Test
    void aNonCsvFileIsRejectedSynchronouslyWithoutCreatingAJob() throws Exception {
        mockMvc.perform(multipart("/api/admin/upload")
                        .file(new MockMultipartFile("file", "marks.xlsx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, "not a csv".getBytes()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Only .csv files are accepted")));

        assertThat(uploadJobs.count()).isZero();
    }

    @Test
    void aHeaderOnlyFileIsRejectedBeforeAJobIsStarted() throws Exception {
        mockMvc.perform(multipart("/api/admin/upload")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv", HEADER.getBytes()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no data rows")));
    }

    @Test
    void theUploadHistoryListsCompletedJobsNewestFirst() throws Exception {
        Long first = upload("/api/admin/upload", adminToken(),
                "samples/results-unit-test-1.csv", "one.csv");
        awaitTerminalStatus(first);
        Long second = upload("/api/admin/upload", adminToken(),
                "samples/results-midterm.csv", "two.csv");
        awaitTerminalStatus(second);

        mockMvc.perform(get("/api/admin/upload")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.intValue()));
    }

    // -- Helpers -------------------------------------------------------------

    private Long upload(String path, String token, String classpathSample, String filename)
            throws Exception {
        byte[] content;
        try (InputStream in = new ClassPathResource(classpathSample).getInputStream()) {
            content = in.readAllBytes();
        }

        String body = mockMvc.perform(multipart(path)
                        .file(new MockMultipartFile("file", filename, "text/csv", content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.uploadJobId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("uploadJobId").asLong();
    }

    /** The import runs on another thread, so the status has to be polled. */
    private UploadJobView awaitTerminalStatus(Long uploadJobId) {
        await().atMost(ofSeconds(60)).pollInterval(ofSeconds(1)).until(() ->
                uploadService.status(uploadJobId).completedAt() != null);
        return uploadService.status(uploadJobId);
    }

    private String adminToken() throws Exception {
        return accessToken("admin");
    }

    private String teacherToken() throws Exception {
        return accessToken("teacher1");
    }

    private String accessToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode tokens = objectMapper.readTree(body);
        return tokens.get("accessToken").asText();
    }
}
