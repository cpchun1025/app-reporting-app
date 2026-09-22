package com.tradingreporting.api.repository;

import com.tradingreporting.api.domain.DailyTradeEntry;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DailyTradeEntryRepository extends JpaRepository<DailyTradeEntry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "business")
    @Query("select e from DailyTradeEntry e where e.id = :id")
    Optional<DailyTradeEntry> findByIdForUpdate(String id);

    @EntityGraph(attributePaths = "business")
    @Query("select e from DailyTradeEntry e where e.businessDate = :businessDate order by e.business.code asc")
    List<DailyTradeEntry> findAllByBusinessDateOrderByBusinessCode(LocalDate businessDate);

    List<DailyTradeEntry> findAllByBusinessDate(LocalDate businessDate);
}
