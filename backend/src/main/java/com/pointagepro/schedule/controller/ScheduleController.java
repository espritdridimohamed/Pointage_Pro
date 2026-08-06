package com.pointagepro.schedule.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.schedule.dto.WorkScheduleRequest;
import com.pointagepro.schedule.dto.WorkScheduleResponse;
import com.pointagepro.schedule.service.ScheduleService;
import com.pointagepro.shared.ApiResponse;
import com.pointagepro.shared.dto.LookupItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserService currentUserService;

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('schedule.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> lookup(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des plannings",
                scheduleService.scheduleLookup(company)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule.read')")
    public ResponseEntity<ApiResponse<List<WorkScheduleResponse>>> list(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des plannings",
                scheduleService.listSchedules(company)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.read')")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> get(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Planning",
                scheduleService.getSchedule(company, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule.write')")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> create(
            @AuthenticationPrincipal User user, @Valid @RequestBody WorkScheduleRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Planning créé", scheduleService.createSchedule(company, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> update(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody WorkScheduleRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Planning mis à jour",
                scheduleService.updateSchedule(company, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        scheduleService.deleteSchedule(company, id);
        return ResponseEntity.ok(ApiResponse.success("Planning supprimé"));
    }
}
