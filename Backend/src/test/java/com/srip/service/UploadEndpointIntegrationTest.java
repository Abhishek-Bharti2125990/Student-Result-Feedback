package com.srip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.domain.UploadJob;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.repository.AiFeedbackRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.TopicScoreRepository;
import com.srip.repository.UploadErrorRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;
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
    private UploadErrorRepository uploadErrors;

    @Autowired
    private AiFeedbackRepository aiFeedback;

    @Autowired
    private com.srip.repository.UploadJobRepository uploadJobs;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void reset() {
        // Child rows first: the foreign keys are real.
        topicScores.deleteAllInBatch();
        examResults.deleteAllInBatch();
        uploadErrors.deleteAllInBatch();
        aiFeedback.deleteAllInBatch();
        uploadJobs.deleteAllInBatch();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void aMultipartUploadIsAcceptedAndTheJobRunsToCompletion() throws Exception {
        Long uploadJobId = upload("samples/results-UT1-2025.csv", "results-UT1-2025.csv");

        UploadJobView finished = awaitTerminalStatus(uploadJobId);

        assertThat(finished.status()).isEqualTo(UploadJob.Status.COMPLETED);
        assertThat(finished.validRecords()).isEqualTo(40);
        assertThat(finished.invalidRecords()).isZero();
        assertThat(finished.jobExecutionId()).isNotNull();
        assertThat(finished.completedAt()).isNotNull();
    }

    @Test
    void aFileWithBadRowsCompletesWithAPerLineErrorReport() throws Exception {
        Long uploadJobId = upload("samples/results-with-errors.csv", "results-with-errors.csv");

        UploadJobView finished = awaitTerminalStatus(uploadJobId);

        assertThat(finished.status()).isEqualTo(UploadJob.Status.COMPLETED_WITH_ERRORS);
        assertThat(finished.validRecords()).isEqualTo(2);
        assertThat(finished.invalidRecords()).isEqualTo(11);
        assertThat(finished.errors()).hasSize(11);
    }

    @Test
    void aNonCsvFileIsRejectedSynchronouslyWithoutCreatingAJob() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "marks.xlsx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, "not a csv".getBytes()))
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Only .csv files are accepted")));
    }

    @Test
    void aHeaderOnlyFileIsRejectedBeforeAJobIsStarted() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv",
                                "admission_no,exam_code,subject_code,marks_obtained,max_marks\n".getBytes()))
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no data rows")));
    }

    @Test
    void theUploadHistoryListsCompletedJobsNewestFirst() throws Exception {
        Long first = upload("samples/results-UT1-2025.csv", "one.csv");
        awaitTerminalStatus(first);
        Long second = upload("samples/results-MID-2025.csv", "two.csv");
        awaitTerminalStatus(second);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/uploads")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.intValue()));
    }

    // -- Helpers -------------------------------------------------------------

    private Long upload(String classpathSample, String filename) throws Exception {
        byte[] content;
        try (InputStream in = new ClassPathResource(classpathSample).getInputStream()) {
            content = in.readAllBytes();
        }

        String body = mockMvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", filename, "text/csv", content))
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.uploadJobId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("uploadJobId").asLong();
    }

    /** The import runs on another thread, so the status has to be polled. */
    private UploadJobView awaitTerminalStatus(Long uploadJobId) {
        await().atMost(ofSeconds(30)).pollInterval(ofSeconds(1)).until(() ->
                uploadService.status(uploadJobId).completedAt() != null);
        return uploadService.status(uploadJobId);
    }

    private String teacherToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher1\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode tokens = objectMapper.readTree(body);
        return tokens.get("accessToken").asText();
    }
}
