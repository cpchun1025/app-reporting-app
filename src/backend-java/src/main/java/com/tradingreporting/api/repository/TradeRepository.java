package com.tradingreporting.api.repository;

import com.tradingreporting.api.domain.Trade;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TradeRepository extends JpaRepository<Trade, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trade t where t.id = :id")
    Optional<Trade> findByIdForUpdate(String id);

    List<Trade> findAllByOrderByTradeDateAscAccountAscIdAsc();

    List<Trade> findAllByTradeDateBetween(LocalDate startDate, LocalDate endDate);
}
