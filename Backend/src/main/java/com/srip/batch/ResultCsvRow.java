package com.srip.batch;

/**
 * One raw CSV row, before any validation or lookup.
 *
 * <p>The file is delivered one <em>topic</em> per line, not one subject per
 * line:
 * <pre>
 * 1001,Ayushman,10,A,Midterm,Mathematics,Algebra,Quadratic Equations,10,25
 * </pre>
 * Several lines therefore describe the same subject paper, and the subject total
 * is the sum of them. That is why nothing here maps to a subject result
 * directly.
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
        String studentId,
        String studentName,
        String className,
        String section,
        String examName,
        String subject,
        String chapterName,
        String topicName,
        String marksObtained,
        String maximumMarks
) {
}
