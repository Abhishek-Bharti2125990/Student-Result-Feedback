package com.srip.batch;

import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.transform.IncorrectTokenCountException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Records every skipped row so the uploader gets a line-by-line report.
 *
 * <p>Skips arrive from two places and carry different information. A parse
 * failure surfaces as {@link FlatFileParseException}, which knows the line
 * number and the raw text but nothing about why the data is wrong. A validation
 * failure surfaces as {@link RowValidationException}, which carries a specific
 * reason. Both are unwrapped here so the stored message is useful either way.
 */
@Component
@StepScope
public class RejectedRowListener implements SkipListener<ResultCsvRow, Object> {

    private final UploadErrorRecorder recorder;
    private final Long uploadJobId;

    public RejectedRowListener(UploadErrorRecorder recorder,
                              @Value("#{jobParameters['uploadJobId']}") Long uploadJobId) {
        this.recorder = recorder;
        this.uploadJobId = uploadJobId;
    }

    @Override
    public void onSkipInRead(Throwable failure) {
        if (failure instanceof FlatFileParseException parseFailure) {
            recorder.record(uploadJobId, parseFailure.getLineNumber(), parseFailure.getInput(),
                    describe(parseFailure));
            return;
        }
        recorder.record(uploadJobId, 0, null, "Could not read row: " + rootMessage(failure));
    }

    @Override
    public void onSkipInProcess(ResultCsvRow row, Throwable failure) {
        recorder.record(uploadJobId, row.lineNumber(), row.rawLine(), rootMessage(failure));
    }

    @Override
    public void onSkipInWrite(Object item, Throwable failure) {
        // A write failure is usually a constraint violation the row-level checks
        // could not see, such as a duplicate line within the same file.
        recorder.record(uploadJobId, 0, String.valueOf(item),
                "Could not save row: " + rootMessage(failure));
    }

    private String describe(FlatFileParseException parseFailure) {
        Throwable cause = parseFailure.getCause();
        if (cause instanceof RowValidationException validation) {
            return validation.getMessage();
        }
        if (cause instanceof IncorrectTokenCountException tokenCount) {
            return "Expected %d columns but found %d"
                    .formatted(tokenCount.getExpectedCount(), tokenCount.getActualCount());
        }
        return "Could not parse line: " + rootMessage(parseFailure);
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        if (current instanceof RowValidationException validation) {
            return validation.getMessage();
        }
        // Prefer a nested validation message over the wrapper's generic text.
        Throwable cause = current.getCause();
        if (cause instanceof RowValidationException validation) {
            return validation.getMessage();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
