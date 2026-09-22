package com.tradingreporting.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "trade_entry_snapshots")
public class TradeEntrySnapshot {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Lob
    @Column(name = "data_json", nullable = false)
    private String dataJson;

    @Column(name = "saved_by_id", length = 36, nullable = false, updatable = false)
    private String savedById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TradeEntrySnapshot() {
    }

    public TradeEntrySnapshot(LocalDate businessDate, String dataJson, String savedById) {
        this.id = UUID.randomUUID().toString();
        this.businessDate = businessDate;
        this.dataJson = dataJson;
        this.savedById = savedById;
    }

    public String getId() {
        return id;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getDataJson() {
        return dataJson;
    }

    public String getSavedById() {
        return savedById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
