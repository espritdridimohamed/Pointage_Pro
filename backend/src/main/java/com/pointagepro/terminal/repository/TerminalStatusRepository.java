package com.pointagepro.terminal.repository;

import com.pointagepro.terminal.entity.TerminalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerminalStatusRepository extends JpaRepository<TerminalStatus, Long> {

    Optional<TerminalStatus> findByCode(String code);
}
