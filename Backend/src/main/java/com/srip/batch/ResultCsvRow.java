package com.srip.batch;

import java.util.List;

/**
 * One raw CSV row, before any validation or lookup.
 *
 * <p>Fields are kept as strings on purpose. If {@code marks_obtained} were
 * parsed to a number here, a row containing "N/A" would fail inside the reader
 * with a type error and no line number, and the uploader would be told only
 * that the file was unparseable. Keeping the text lets the processor reject the
 * row with a message naming the column and the offending value.
 *
 * @param lineNumber 1-based line in the source file, used in error reports
 * @param rawLine    the original text, echoed back so the uploader can find it
 */
public record ResultCsvRow(
        int lineNumber,
        String rawLine,
        String admissionNo,
        String examCode,
        String subjectCode,
        String marksObtained,
        String maxMarks,
        String attempted,
        String remarks,
        List<TopicMark> topicMarks
) {

    /** One topic entry parsed out of the {@code topic_breakdown} column. */
    public record TopicMark(String topicName, String marksObtained, String maxMarks) {
    }
}
