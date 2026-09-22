package com.srip.batch;

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
import java.util.Arrays;
import java.util.List;

/**
 * Checks the header row before any data is imported.
 *
 * <p>This runs as its own step so a file with the wrong columns fails once,
 * with one clear message, instead of producing a rejection for every single
 * row. Getting 900 identical "unknown subject_code" errors because the columns
 * were in the wrong order tells the uploader almost nothing.
 */
@Component
@StepScope
public class CsvHeaderValidationTasklet implements Tasklet {

    private static final int REQUIRED_COLUMN_COUNT = ResultCsvLineMapper.REQUIRED_COLUMNS;

    private final Path filePath;

    public CsvHeaderValidationTasklet(@Value("#{jobParameters['filePath']}") String filePath) {
        this.filePath = Path.of(filePath);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        if (!Files.isReadable(filePath)) {
            throw new IllegalStateException("Uploaded file is not readable: " + filePath);
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || header.isBlank()) {
                throw new IllegalStateException("The uploaded file is empty");
            }

            List<String> actual = Arrays.stream(header.split(","))
                    // Strip a UTF-8 byte order mark, which spreadsheet exports add
                    // and which would otherwise corrupt the first column name.
                    .map(column -> column.replace("﻿", "").trim().toLowerCase())
                    .toList();

            List<String> required = List.of(ResultCsvLineMapper.COLUMNS)
                    .subList(0, REQUIRED_COLUMN_COUNT);

            if (actual.size() < REQUIRED_COLUMN_COUNT || !actual.subList(0, REQUIRED_COLUMN_COUNT).equals(required)) {
                throw new IllegalStateException(
                        "Unexpected CSV header. The first %d columns must be exactly: %s. Found: %s"
                                .formatted(REQUIRED_COLUMN_COUNT, String.join(", ", required),
                                        String.join(", ", actual)));
            }
        }

        return RepeatStatus.FINISHED;
    }
}
