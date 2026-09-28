package com.srip.batch;

import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.domain.Topic;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.SubjectRepository;
import com.srip.repository.TopicRepository;
import com.srip.repository.TopicScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates the reference rows the file refers to, before any marks are imported.
 *
 * <p>The CSV is self-describing - it names the student, the class, the exam, the
 * subject and the topic - so a file may legitimately introduce people and
 * curriculum that are not in the database yet. The obvious place to create them
 * is the item processor, and that is the wrong place: a chunk-oriented step
 * rolls the whole chunk back when one row fails, so a subject created while
 * processing row 12 disappears when row 15 is rejected. Any id cached in memory
 * is then a dangling reference and every later row in the file fails on a
 * foreign key.
 *
 * <p>Doing it here, in a separate step that commits before the import step
 * starts, removes that failure mode entirely: by the time rows are processed
 * every lookup is a read of committed data.
 *
 * <p>This step also clears the topic breakdown of every subject paper the file
 * touches. Re-uploading a corrected file is normal, and if the new version drops
 * a topic, the stale row would otherwise stay behind and the subject total would
 * never agree with the file it came from.
 *
 * <p>Malformed rows are ignored rather than rejected here. Reporting them is the
 * import step's job, where a failure can be attributed to a line number; failing
 * twice for the same row would double every error count.
 */
@Component
@StepScope
public class ReferenceDataTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataTasklet.class);

    private static final int SUBJECT_CODE_LENGTH = 32;
    private static final int EXAM_CODE_LENGTH = 64;
    private static final String DEFAULT_TERM = "TERM_1";

    private final Path filePath;
    private final StudentRepository students;
    private final ExamRepository exams;
    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final ExamResultRepository examResults;
    private final TopicScoreRepository topicScores;

    public ReferenceDataTasklet(@Value("#{jobParameters['filePath']}") String filePath,
                                StudentRepository students,
                                ExamRepository exams,
                                SubjectRepository subjects,
                                TopicRepository topics,
                                ExamResultRepository examResults,
                                TopicScoreRepository topicScores) {
        this.filePath = Path.of(filePath);
        this.students = students;
        this.exams = exams;
        this.subjects = subjects;
        this.topics = topics;
        this.examResults = examResults;
        this.topicScores = topicScores;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        Scan scan = scanFile();

        scan.studentsById.forEach(this::ensureStudent);
        scan.examsByName.forEach(this::ensureExam);
        scan.subjectsByName.forEach(this::ensureSubject);
        scan.topicKeys.forEach(this::ensureTopic);

        int cleared = clearTouchedBreakdowns(scan.papers);

        log.info("Reference data ready: {} student(s), {} exam(s), {} subject(s), {} topic(s); "
                        + "cleared {} existing subject breakdown(s)",
                scan.studentsById.size(), scan.examsByName.size(),
                scan.subjectsByName.size(), scan.topicKeys.size(), cleared);

        return RepeatStatus.FINISHED;
    }

    // -- Reading the file ----------------------------------------------------

    private Scan scanFile() throws IOException {
        Scan scan = new Scan();
        ResultCsvLineMapper mapper = new ResultCsvLineMapper();

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            reader.readLine(); // header, already validated by the previous step

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                ResultCsvRow row;
                try {
                    row = mapper.mapLine(line, lineNumber);
                } catch (RuntimeException e) {
                    // The import step will reject this row with its line number.
                    continue;
                }
                collect(scan, row);
            }
        }
        return scan;
    }

    private void collect(Scan scan, ResultCsvRow row) {
        if (row.studentId() == null || row.examName() == null || row.subject() == null
                || row.topicName() == null || row.className() == null || row.studentName() == null) {
            return;
        }

        scan.studentsById.putIfAbsent(row.studentId(),
                new StudentDetails(row.studentName(), row.className(), row.section()));
        scan.examsByName.putIfAbsent(row.examName(), row.className());
        scan.subjectsByName.putIfAbsent(row.subject(), row.className());
        scan.topicKeys.add(new TopicKey(row.subject(), row.chapterName(), row.topicName()));
        scan.papers.add(new PaperKey(row.studentId(), row.examName(), row.subject()));
    }

    // -- Upserts -------------------------------------------------------------

    private void ensureStudent(String studentId, StudentDetails details) {
        students.findByAdmissionNo(studentId).ifPresentOrElse(existing -> {
            // A student who has changed section mid-year should be moved, not
            // duplicated; the file is the more recent source of truth.
            existing.setFullName(details.name());
            existing.setClassName(details.className());
            existing.setSection(details.section());
            students.save(existing);
        }, () -> students.save(new Student(
                studentId, details.name(), details.className(), details.section(), academicYear())));
    }

    private void ensureExam(String examName, String className) {
        String code = CsvSlug.of(examName, EXAM_CODE_LENGTH);
        if (exams.findByCode(code).isPresent()) {
            return;
        }
        // An exam the calendar does not know about is dated today. Trends are
        // ordered by exam_date, so a backdated paper should be seeded in
        // migration V2 rather than introduced by a file.
        exams.save(new Exam(code, examName, DEFAULT_TERM, LocalDate.now(), className, academicYear()));
    }

    private void ensureSubject(String subjectName, String className) {
        String code = CsvSlug.of(subjectName, SUBJECT_CODE_LENGTH);
        if (subjects.findByCode(code).isPresent()) {
            return;
        }
        subjects.save(new Subject(code, subjectName, className));
    }

    private void ensureTopic(TopicKey key) {
        Subject subject = subjects.findByCode(CsvSlug.of(key.subjectName(), SUBJECT_CODE_LENGTH))
                .orElseThrow(() -> new IllegalStateException(
                        "Subject '%s' was not created before its topics".formatted(key.subjectName())));

        topics.findBySubjectIdAndNameIgnoreCase(subject.getId(), key.topicName())
                .ifPresentOrElse(existing -> {
                    // Backfill a chapter for a topic that was seeded without one.
                    if (existing.getChapterName() == null && key.chapterName() != null) {
                        existing.setChapterName(key.chapterName());
                        topics.save(existing);
                    }
                }, () -> topics.save(new Topic(subject, key.chapterName(), key.topicName())));
    }

    /** @return how many existing subject breakdowns were emptied */
    private int clearTouchedBreakdowns(Set<PaperKey> papers) {
        List<Long> resultIds = new ArrayList<>();
        for (PaperKey paper : papers) {
            Long studentId = students.findByAdmissionNo(paper.studentId())
                    .map(Student::getId)
                    .orElse(null);
            Long examId = exams.findByCode(CsvSlug.of(paper.examName(), EXAM_CODE_LENGTH))
                    .map(Exam::getId)
                    .orElse(null);
            Long subjectId = subjects.findByCode(CsvSlug.of(paper.subjectName(), SUBJECT_CODE_LENGTH))
                    .map(Subject::getId)
                    .orElse(null);
            if (studentId == null || examId == null || subjectId == null) {
                continue;
            }
            examResults.findByStudentIdAndExamIdAndSubjectId(studentId, examId, subjectId)
                    .map(ExamResult::getId)
                    .ifPresent(resultIds::add);
        }

        if (resultIds.isEmpty()) {
            return 0;
        }
        topicScores.deleteByExamResultIds(resultIds);
        return resultIds.size();
    }

    /**
     * @return the academic year as {@code 2025-2026}, rolling over in June so a
     *         file uploaded in July belongs to the year that is starting
     */
    private static String academicYear() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= Month.JUNE.getValue()
                ? today.getYear()
                : today.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }

    // -- What one pass over the file found -----------------------------------

    private static final class Scan {
        private final Map<String, StudentDetails> studentsById = new LinkedHashMap<>();
        /** Exam name -> the class it was sat by, for an exam not in the calendar. */
        private final Map<String, String> examsByName = new LinkedHashMap<>();
        /** Subject name -> the class it is taught to. */
        private final Map<String, String> subjectsByName = new LinkedHashMap<>();
        private final Set<TopicKey> topicKeys = new LinkedHashSet<>();
        private final Set<PaperKey> papers = new LinkedHashSet<>();
    }

    private record StudentDetails(String name, String className, String section) {
    }

    private record TopicKey(String subjectName, String chapterName, String topicName) {
    }

    /** One subject paper for one student in one exam. */
    private record PaperKey(String studentId, String examName, String subjectName) {
    }
}
