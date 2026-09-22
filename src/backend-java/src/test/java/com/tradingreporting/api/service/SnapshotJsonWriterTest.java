package com.tradingreporting.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SnapshotJsonWriterTest {

    private final SnapshotJsonWriter writer = new SnapshotJsonWriter(new ObjectMapper());

    @Test
    void writesMapKeysInCanonicalOrderAtEveryLevel() {
        Map<String, Object> payload = Map.of(
                "z", 1,
                "a", Map.of("y", true, "b", List.of(Map.of("d", 2, "c", 3))));

        String result = writer.write(payload);

        assertThat(result).isEqualTo("{\"a\":{\"b\":[{\"c\":3,\"d\":2}],\"y\":true},\"z\":1}");
    }

    @Test
    void preservesListOrderWhileCanonicalizingContainedMaps() {
        String result = writer.write(List.of(Map.of("b", 2, "a", 1), "second"));

        assertThat(result).isEqualTo("[{\"a\":1,\"b\":2},\"second\"]");
    }
}
