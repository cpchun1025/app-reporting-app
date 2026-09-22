package com.tradingreporting.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.exception.ApiException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class BackupCopyWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesBusinessDateAndSavedDataToANewJsonFile() throws Exception {
        BackupCopyWriter writer = new BackupCopyWriter(properties(temporaryDirectory), new ObjectMapper());

        String filename = writer.write(LocalDate.of(2026, 9, 22), Map.of("id", "trade-1"));

        assertThat(filename).matches("trade-entry-\\d{8}-\\d{6}-\\d{6}\\.json");
        assertThat(Files.readString(temporaryDirectory.resolve(filename)))
                .isEqualTo("{\n  \"business_date\": \"2026-09-22\",\n  \"saved_data\": {\"id\":\"trade-1\"}\n}");
    }

    @Test
    void writeOrFailConvertsFileSystemFailuresToApiErrors() {
        Path fileInsteadOfDirectory = temporaryDirectory.resolve("not-a-directory");
        assertThatCode(() -> Files.writeString(fileInsteadOfDirectory, "content")).doesNotThrowAnyException();
        BackupCopyWriter writer = new BackupCopyWriter(properties(fileInsteadOfDirectory), new ObjectMapper());

        assertThatThrownBy(() -> writer.writeOrFail(LocalDate.of(2026, 9, 22), Map.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void writeExposesFileSystemFailuresAsUncheckedIoExceptions() {
        Path fileInsteadOfDirectory = temporaryDirectory.resolve("not-a-directory");
        assertThatCode(() -> Files.writeString(fileInsteadOfDirectory, "content")).doesNotThrowAnyException();
        BackupCopyWriter writer = new BackupCopyWriter(properties(fileInsteadOfDirectory), new ObjectMapper());

        assertThatThrownBy(() -> writer.write(LocalDate.of(2026, 9, 22), Map.of()))
                .isInstanceOf(UncheckedIOException.class);
    }

    private static AppProperties properties(Path path) {
        return new AppProperties(
                "unit-test-secret", "HS256", 60, false, "", "", false, "", path.toString(), LocalDate.of(2026, 9, 22));
    }
}
