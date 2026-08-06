package com.pointagepro.terminal.repository;

import com.pointagepro.terminal.entity.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {

    Optional<Terminal> findBySerialNumber(String serialNumber);

    Optional<Terminal> findByCode(String code);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsByCode(String code);

    long countByLocationId(Long locationId);
}
