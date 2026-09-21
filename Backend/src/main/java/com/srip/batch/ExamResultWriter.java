package com.srip.batch;

import com.srip.domain.ExamResult;
import com.srip.repository.ExamResultRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persists a chunk of results.
 *
 * <p>Step-scoped so it can read the upload job id from the job parameters and
 * stamp it onto every row. That stamp is what lets an operator answer "which
 * upload produced this mark?" months later, which matters when a mark is
 * disputed.
 *
 * <p>The processor has already resolved insert-versus-update, so this only has
 * to hand each entity to the repository.
 */
@Component
@StepScope
public class ExamResultWriter implements ItemWriter<ExamResult> {

    private final ExamResultRepository examResults;
    private final Long uploadJobId;

    public ExamResultWriter(ExamResultRepository examResults,
                            @Value("#{jobParameters['uploadJobId']}") Long uploadJobId) {
        this.examResults = examResults;
        this.uploadJobId = uploadJobId;
    }

    @Override
    public void write(Chunk<? extends ExamResult> chunk) {
        for (ExamResult result : chunk) {
            result.setUploadJobId(uploadJobId);
            examResults.save(result);
        }
    }
}
