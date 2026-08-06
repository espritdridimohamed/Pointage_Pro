package com.pointagepro.legal.repository;

import com.pointagepro.legal.entity.TaxBracket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxBracketRepository extends JpaRepository<TaxBracket, Long> {

    List<TaxBracket> findByYearOrderByBracketOrderAsc(Integer year);
}
