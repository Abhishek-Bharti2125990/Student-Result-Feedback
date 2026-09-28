package com.srip.batch;

import java.math.BigDecimal;

/**
 * A CSV row that has survived validation: every reference resolved to an id and
 * every number parsed.
 *
 * <p>Ids rather than entities. A chunk-oriented step commits each chunk, so an
 * entity attached here would be detached by the time the writer ran in the next
 * transaction; the writer loads what it needs from these ids instead.
 *
 * @param lineNumber kept so a failure in the writer can still name the line
 * @param subjectName carried for the writer's log and error messages, which are
 *                    read by whoever uploaded the file and mean nothing in ids
 */
public record TopicResultRow(
        int lineNumber,
        String rawLine,
        Long studentId,
        Long examId,
        Long subjectId,
        Long topicId,
        String subjectName,
        String topicName,
        BigDecimal marksObtained,
        BigDecimal maxMarks
) {

    /** Identifies the subject paper this row contributes to. */
    public PaperId paperId() {
        return new PaperId(studentId, examId, subjectId);
    }

    public record PaperId(Long studentId, Long examId, Long subjectId) {
    }
}
