package com.pointagepro.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompanySettingsService {

    private static final Logger log = LoggerFactory.getLogger(CompanySettingsService.class);
    private static final Long SETTINGS_ID = 1L;

    private final CompanySettingsRepository repository;

    public CompanySettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanySettings get() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            CompanySettings settings = new CompanySettings();
            settings.setId(SETTINGS_ID);
            return repository.save(settings);
        });
    }

    public CompanySettings update(CompanySettings updates) {
        CompanySettings existing = get();
        existing.setCompanyName(updates.getCompanyName());
        existing.setCompanySector(updates.getCompanySector());
        existing.setCompanyAddress(updates.getCompanyAddress());
        existing.setCompanyEmail(updates.getCompanyEmail());
        existing.setCompanyPhone(updates.getCompanyPhone());
        if (updates.getCompanyLogo() != null) {
            existing.setCompanyLogo(updates.getCompanyLogo());
        }
        existing.setLateGraceMinutes(updates.getLateGraceMinutes());
        existing.setOvertimeRate(updates.getOvertimeRate());
        existing.setCnssRate(updates.getCnssRate());
        existing.setCnssEmployerRate(updates.getCnssEmployerRate());
        existing.setCnssCeiling(updates.getCnssCeiling());
        existing.setAssuranceRate(updates.getAssuranceRate());
        existing.setIrTranche1(updates.getIrTranche1());
        existing.setIrRate1(updates.getIrRate1());
        existing.setIrTranche2(updates.getIrTranche2());
        existing.setIrRate2(updates.getIrRate2());
        existing.setIrTranche3(updates.getIrTranche3());
        existing.setIrRate3(updates.getIrRate3());
        existing.setIrTranche4(updates.getIrTranche4());
        existing.setIrRate4(updates.getIrRate4());
        existing.setIrTranche5(updates.getIrTranche5());
        existing.setIrRate5(updates.getIrRate5());
        existing.setIrAbatement(updates.getIrAbatement());
        existing.setLanguage(updates.getLanguage());
        existing.setTheme(updates.getTheme());

        CompanySettings saved = repository.save(existing);
        log.info("Company settings updated");
        return saved;
    }
}
