package com.srip.batch;

/**
 * Thrown for a row that is individually invalid.
 *
 * <p>The step is configured to skip this exception rather than fail, so one bad
 * row in a thousand does not abandon the other 999. It carries the line number
 * and original text so the skip listener can record a report the uploader can
 * act on.
 */
public class RowValidationException extends RuntimeException {

    private final int lineNumber;
    private final String rawLine;

    public RowValidationException(int lineNumber, String rawLine, String message) {
        super(message);
        this.lineNumber = lineNumber;
        this.rawLine = rawLine;
    }

    public static RowValidationException of(ResultCsvRow row, String message) {
        return new RowValidationException(row.lineNumber(), row.rawLine(), message);
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getRawLine() {
        return rawLine;
    }
}
