package com.pointagepro.attendance.controller;

import com.pointagepro.attendance.dto.AttendanceSummaryResponse;
import com.pointagepro.attendance.dto.RecomputeAllRequest;
import com.pointagepro.attendance.dto.RecomputeRequest;
import com.pointagepro.attendance.dto.RecomputeStats;
import com.pointagepro.attendance.service.AttendanceSummaryService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Day summaries, day status and the explicit recompute trigger. Controllers only validate and
 * map — tenant checks, range rules and compute-on-miss live in {@link AttendanceSummaryService}.
 */
@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceSummaryController {

    private final AttendanceSummaryService attendanceSummaryService;
    private final CurrentUserService currentUserService;

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<List<AttendanceSummaryResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Company company = currentUserService.requireCompany(user);
        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();

        List<AttendanceSummaryResponse> data = attendanceSummaryService
                .listForEmployee(company, employeeId, fromDate, toDate).stream()
                .map(AttendanceSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Liste des synthèses", data));
    }

    @GetMapping("/summaries/{id}")
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> getById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        AttendanceSummaryResponse data =
                AttendanceSummaryResponse.from(attendanceSummaryService.getById(company, id));
        return ResponseEntity.ok(ApiResponse.success("Synthèse", data));
    }

    @GetMapping("/summaries/day")
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> day(
            @AuthenticationPrincipal User user,
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Company company = currentUserService.requireCompany(user);
        LocalDate target = date != null ? date : LocalDate.now();
        AttendanceSummaryResponse data = AttendanceSummaryResponse.from(
                attendanceSummaryService.day(company, employeeId, target, user));
        return ResponseEntity.ok(ApiResponse.success("Synthèse du jour", data));
    }

    @GetMapping("/summaries/today")
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> today(
            @AuthenticationPrincipal User user) {

        Company company = currentUserService.requireCompany(user);
        Long employeeId = currentUserService.requireEmployee(user).getId();
        AttendanceSummaryResponse data = AttendanceSummaryResponse.from(
                attendanceSummaryService.day(company, employeeId, LocalDate.now(), user));
        return ResponseEntity.ok(ApiResponse.success("Synthèse du jour", data));
    }

    @PostMapping("/recompute")
    @PreAuthorize("hasAuthority('attendance.recalculate')")
    public ResponseEntity<ApiResponse<List<AttendanceSummaryResponse>>> recompute(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RecomputeRequest request) {

        Company company = currentUserService.requireCompany(user);
        List<AttendanceSummaryResponse> data = attendanceSummaryService
                .recomputeForEmployee(company, request.getEmployeeId(), request.getFrom(),
                        request.getTo(), user, request.getReason()).stream()
                .map(AttendanceSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Recalcul terminé", data));
    }

    @PostMapping("/recompute/all")
    @PreAuthorize("hasAuthority('attendance.recalculate')")
    public ResponseEntity<ApiResponse<RecomputeStats>> recomputeAll(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RecomputeAllRequest request) {

        Company company = currentUserService.requireCompany(user);
        RecomputeStats data = attendanceSummaryService.recomputeCompany(
                company, request.getFrom(), request.getTo(), user, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Recalcul de masse terminé", data));
    }
}
