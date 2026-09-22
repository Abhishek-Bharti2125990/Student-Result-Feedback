package com.srip.batch;

import com.srip.domain.UploadError;
import com.srip.repository.UploadErrorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes rejected rows to {@code upload_errors}.
 *
 * <p>Each insert runs in its own transaction. A skip callback fires around a
 * chunk that is being rolled back, so joining that transaction would discard
 * the error record along with the bad data - the uploader would see a failure
 * count with no explanation of what failed.
 */
@Service
public class UploadErrorRecorder {

    private final UploadErrorRepository uploadErrors;

    public UploadErrorRecorder(UploadErrorRepository uploadErrors) {
        this.uploadErrors = uploadErrors;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long uploadJobId, int lineNumber, String rawLine, String message) {
        uploadErrors.save(new UploadError(uploadJobId, lineNumber, rawLine, message));
    }
}
