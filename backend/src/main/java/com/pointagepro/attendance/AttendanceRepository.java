package com.pointagepro.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByEmployeeIdAndDateBetweenOrderByDateAsc(Long employeeId, LocalDate start, LocalDate end);

    List<Attendance> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);

    @Query("SELECT a FROM Attendance a WHERE a.date BETWEEN :start AND :end AND a.employeeId = :employeeId ORDER BY a.date ASC")
    List<Attendance> findByMonth(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(a.overtimeHours), 0) FROM Attendance a WHERE a.employeeId = :employeeId AND a.date BETWEEN :start AND :end")
    BigDecimal sumOvertimeHours(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(a.lateMinutes), 0) FROM Attendance a WHERE a.employeeId = :employeeId AND a.date BETWEEN :start AND :end")
    Integer sumLateMinutes(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(a.lateMinutes), 0) FROM Attendance a WHERE a.employeeId = :employeeId AND a.date BETWEEN :start AND :end AND a.lateMinutes > :graceMinutes")
    Integer sumEffectiveLateMinutes(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("graceMinutes") int graceMinutes);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.employeeId = :employeeId AND a.date BETWEEN :start AND :end AND a.status != 'ABSENT'")
    Integer countDaysWorked(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.employeeId = :employeeId AND a.date BETWEEN :start AND :end AND a.status = 'ABSENT'")
    Integer countDaysAbsent(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT a.date FROM Attendance a WHERE a.employeeId = :employeeId AND a.date BETWEEN :start AND :end AND a.status = 'ABSENT'")
    List<LocalDate> findAbsentDates(@Param("employeeId") Long employeeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    boolean existsByEmployeeIdAndDate(Long employeeId, LocalDate date);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.date BETWEEN :start AND :end AND (a.status = 'ABSENT' OR a.status IS NULL)")
    long countAbsentDays(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE YEAR(a.date) = :year AND (a.status = 'ABSENT' OR a.status IS NULL)")
    long countAbsentDaysByYear(@Param("year") int year);

    @Query("SELECT COUNT(DISTINCT a.employeeId) FROM Attendance a WHERE a.date BETWEEN :start AND :end")
    Integer countDistinctEmployeesPresent(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(a.lateMinutes), 0) FROM Attendance a WHERE a.date BETWEEN :start AND :end")
    Integer sumAllLateMinutes(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
