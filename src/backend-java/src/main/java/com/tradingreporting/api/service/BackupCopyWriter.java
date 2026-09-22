package com.tradingreporting.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.exception.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Writes an audit backup copy of every trade-entry save to disk, mirroring the Python backend's
 * {@code write_backup_copy}: a temp file is written and fsynced, then atomically renamed into
 * place so a crash never leaves a partially written backup file.
 */
@Component
public class BackupCopyWriter {

    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSSSSS");

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public BackupCopyWriter(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Writes {@code {"business_date": ..., "saved_data": ...}} and returns the created filename. */
    public String write(LocalDate businessDate, Object savedData) {
        Path outputDir = Path.of(properties.tradeSaveCopyPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputDir);
            String filename = "trade-entry-" + STAMP_FORMAT.format(LocalDateTime.now()) + ".json";
            Path target = outputDir.resolve(filename);
            Path temp = Files.createTempFile(outputDir, ".trade-entry-", ".tmp");
            try {
                String savedDataJson = objectMapper.writeValueAsString(savedData);
                String content = "{\n  \"business_date\": \"" + businessDate + "\",\n  \"saved_data\": "
                        + savedDataJson + "\n}";
                Files.writeString(temp, content);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            return filename;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Wraps {@link #write} so I/O failures translate to the same 500 the Python backend returns. */
    public String writeOrFail(LocalDate businessDate, Object savedData) {
        try {
            return write(businessDate, savedData);
        } catch (UncheckedIOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Trade data was not saved because the server backup copy could not be written.");
        }
    }
}
