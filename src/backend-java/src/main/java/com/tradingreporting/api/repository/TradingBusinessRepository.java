package com.tradingreporting.api.repository;

import com.tradingreporting.api.domain.TradingBusiness;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingBusinessRepository extends JpaRepository<TradingBusiness, String> {
    List<TradingBusiness> findAllByActiveTrueOrderByCodeAsc();

    Optional<TradingBusiness> findByCode(String code);
}
