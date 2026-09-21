package com.srip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where uploaded CSV files are staged, bound from {@code app.upload.*}.
 *
 * <p>The batch job reads from disk rather than from the request body: the HTTP
 * call returns as soon as the file is stored, and the job can be restarted
 * later against the same file without a re-upload.
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(String directory) {

    public UploadProperties {
        directory = (directory == null || directory.isBlank()) ? "./data/uploads" : directory;
    }
}
