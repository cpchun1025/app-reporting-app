package com.tradingreporting.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "daily_trade_entries",
        uniqueConstraints = @UniqueConstraint(name = "uq_daily_trade_entries_business_date",
                columnNames = {"business_id", "business_date"}))
public class DailyTradeEntry {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private TradingBusiness business;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "delta", precision = 20, scale = 4, nullable = false)
    private BigDecimal delta = BigDecimal.ZERO;

    @Column(name = "gamma", precision = 20, scale = 4, nullable = false)
    private BigDecimal gamma = BigDecimal.ZERO;

    @Column(name = "theta", precision = 20, scale = 4, nullable = false)
    private BigDecimal theta = BigDecimal.ZERO;

    @Column(name = "vega", precision = 20, scale = 4, nullable = false)
    private BigDecimal vega = BigDecimal.ZERO;

    @Column(name = "pnl", precision = 20, scale = 4, nullable = false)
    private BigDecimal pnl = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_by_id", length = 36, nullable = false, updatable = false)
    private String createdById;

    @Column(name = "updated_by_id", length = 36)
    private String updatedById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "locked_by_id", length = 36)
    private String lockedById;

    @Column(name = "locked_by_display_name", length = 200)
    private String lockedByDisplayName;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "lock_expires_at")
    private OffsetDateTime lockExpiresAt;

    protected DailyTradeEntry() {
    }

    public DailyTradeEntry(TradingBusiness business, LocalDate businessDate, String createdById) {
        this.id = UUID.randomUUID().toString();
        this.business = business;
        this.businessDate = businessDate;
        this.createdById = createdById;
        this.updatedById = createdById;
    }

    public String getId() {
        return id;
    }

    public TradingBusiness getBusiness() {
        return business;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public void setDelta(BigDecimal delta) {
        this.delta = delta;
    }

    public BigDecimal getGamma() {
        return gamma;
    }

    public void setGamma(BigDecimal gamma) {
        this.gamma = gamma;
    }

    public BigDecimal getTheta() {
        return theta;
    }

    public void setTheta(BigDecimal theta) {
        this.theta = theta;
    }

    public BigDecimal getVega() {
        return vega;
    }

    public void setVega(BigDecimal vega) {
        this.vega = vega;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public int getVersion() {
        return version;
    }

    public String getCreatedById() {
        return createdById;
    }

    public String getUpdatedById() {
        return updatedById;
    }

    public void setUpdatedById(String updatedById) {
        this.updatedById = updatedById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getLockedById() {
        return lockedById;
    }

    public void setLockedById(String lockedById) {
        this.lockedById = lockedById;
    }

    public String getLockedByDisplayName() {
        return lockedByDisplayName;
    }

    public void setLockedByDisplayName(String lockedByDisplayName) {
        this.lockedByDisplayName = lockedByDisplayName;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(OffsetDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public OffsetDateTime getLockExpiresAt() {
        return lockExpiresAt;
    }

    public void setLockExpiresAt(OffsetDateTime lockExpiresAt) {
        this.lockExpiresAt = lockExpiresAt;
    }
}
