package com.tradingreporting.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "account", length = 100, nullable = false)
    private String account;

    @Column(name = "instrument", length = 100, nullable = false)
    private String instrument;

    @Column(name = "side", length = 4, nullable = false)
    private String side;

    @Column(name = "quantity", precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "price", precision = 18, scale = 4, nullable = false)
    private BigDecimal price;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_by_id", length = 36, nullable = false, updatable = false)
    private String createdById;

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

    protected Trade() {
    }

    public Trade(LocalDate tradeDate, String account, String instrument, String side, BigDecimal quantity,
                 BigDecimal price, String currency, String status, String notes, String createdById) {
        this.id = UUID.randomUUID().toString();
        this.tradeDate = tradeDate;
        this.account = account;
        this.instrument = instrument;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.currency = currency;
        this.status = status;
        this.notes = notes;
        this.createdById = createdById;
    }

    public String getId() {
        return id;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getVersion() {
        return version;
    }

    public String getCreatedById() {
        return createdById;
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
