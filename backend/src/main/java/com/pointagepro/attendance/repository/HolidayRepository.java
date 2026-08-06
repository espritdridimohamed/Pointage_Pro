package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByCompanyIdAndHolidayDateBetweenOrderByHolidayDateAsc(Long companyId,
                                                                            LocalDate from,
                                                                            LocalDate to);

    Optional<Holiday> findByCompanyIdAndHolidayDate(Long companyId, LocalDate holidayDate);

    boolean existsByCompanyIdAndHolidayDate(Long companyId, LocalDate holidayDate);
}
