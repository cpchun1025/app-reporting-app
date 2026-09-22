package com.tradingreporting.api.dto;

import java.time.OffsetDateTime;

public record TradeEntrySaveResponse(OffsetDateTime savedAt, String snapshotFilename, int rowCount) {
}
