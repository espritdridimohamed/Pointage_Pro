package com.pointagepro.attendance.controller;

import com.pointagepro.attendance.dto.AttendanceEventRequest;
import com.pointagepro.attendance.dto.AttendanceEventResponse;
import com.pointagepro.attendance.dto.AttendanceEventResult;
import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.attendance.service.AttendanceEventService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manual IN/OUT entry and raw-event listing for authenticated staff. Company is resolved
 * from the authenticated user's employee record; the target employee must be in the same
 * company. Controllers only validate and map — all rules live in the services.
 */
@RestController
@RequestMapping("/attendance/events")
@RequiredArgsConstructor
public class AttendanceEventController {

    private final AttendanceEventService attendanceEventService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('attendance.write')")
    public ResponseEntity<ApiResponse<AttendanceEventResponse>> record(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AttendanceEventRequest request) {

        Company company = currentUserService.requireCompany(user);
        AttendanceEventResult result = attendanceEventService.recordManual(
                company, request.getEmployeeId(), request.getEventType(),
                request.getTimestamp(), user);

        AttendanceEventResponse response = AttendanceEventResponse.from(result.event());
        response.setComputedBy(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pointage enregistré", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<List<AttendanceEventResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Company company = currentUserService.requireCompany(user);
        LocalDateTime fromTime = (from != null ? from : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime toTime = (to != null ? to : LocalDate.now()).atTime(23, 59, 59);

        List<AttendanceEvent> events =
                attendanceEventService.listForEmployee(company, employeeId, fromTime, toTime);
        List<AttendanceEventResponse> data = events.stream()
                .map(AttendanceEventResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Liste des pointages", data));
    }
}
