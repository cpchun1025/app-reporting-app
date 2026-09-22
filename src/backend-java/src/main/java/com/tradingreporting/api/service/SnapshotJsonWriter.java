package com.tradingreporting.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Serializes save-request payloads into a compact, key-sorted JSON string for the audit
 * {@code trade_entry_snapshots.data_json} column, mirroring the Python backend's
 * {@code json.dumps(..., sort_keys=True)} canonicalization.
 */
@Component
public class SnapshotJsonWriter {

    private final ObjectMapper objectMapper;

    public SnapshotJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object payload) {
        try {
            Object tree = canonicalize(objectMapper.convertValue(payload, Object.class));
            return objectMapper.writeValueAsString(tree);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to serialize snapshot payload.", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SnapshotJsonWriter::canonicalize).toList();
        }
        return value;
    }
}
