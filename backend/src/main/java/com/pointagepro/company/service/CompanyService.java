package com.pointagepro.company.service;

import com.pointagepro.company.dto.CompanyResponse;
import com.pointagepro.company.entity.Company;
import com.pointagepro.company.entity.CompanySettings;
import com.pointagepro.company.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only company view (single-company deployment). Only {@code company.read} is exposed.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanySettingsRepository settingsRepository;

    public CompanyResponse get(Company company) {
        CompanySettings settings = settingsRepository.findByCompanyId(company.getId()).orElse(null);
        return CompanyResponse.from(company, settings);
    }
}
