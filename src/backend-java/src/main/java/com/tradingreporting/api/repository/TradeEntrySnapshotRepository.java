package com.tradingreporting.api.repository;

import com.tradingreporting.api.domain.TradeEntrySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeEntrySnapshotRepository extends JpaRepository<TradeEntrySnapshot, String> {
}
