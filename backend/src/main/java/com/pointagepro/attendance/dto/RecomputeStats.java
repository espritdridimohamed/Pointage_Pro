package com.pointagepro.attendance.dto;

import java.time.LocalDate;

/**
 * Service → controller outcome of a company-wide recompute: how many employees were
 * recomputed and how many summary rows were upserted (employeeCount × days in range).
 */
public record RecomputeStats(Long companyId, LocalDate from, LocalDate to,
                             int employeeCount, int dayCount) {
}
