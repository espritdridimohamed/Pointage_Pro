package com.pointagepro.leave;

import com.pointagepro.leave.dto.LeaveBalanceResponse;
import com.pointagepro.leave.dto.LeaveRequestCreate;
import com.pointagepro.leave.dto.LeaveRequestResponse;
import com.pointagepro.shared.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/leaves")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false, defaultValue = "newest") String sort) {
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved",
                leaveRequestService.getAll(search, status, leaveType, sort)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave request retrieved", leaveRequestService.getById(id)));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getByEmployeeId(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved", leaveRequestService.getByEmployeeId(employeeId)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved", leaveRequestService.getByStatus(status)));
    }

    @GetMapping("/balance/{employeeId}")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getBalance(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.success("Leave balance retrieved", leaveRequestService.getBalance(employeeId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved", Map.of(
                "pending", leaveRequestService.countPending(),
                "approved", leaveRequestService.countApproved(),
                "refused", leaveRequestService.countRefused()
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> create(@Valid @RequestBody LeaveRequestCreate request) {
        return ResponseEntity.ok(ApiResponse.success("Leave request created", leaveRequestService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> update(@PathVariable Long id,
                                                                     @Valid @RequestBody LeaveRequestCreate request) {
        return ResponseEntity.ok(ApiResponse.success("Leave request updated", leaveRequestService.update(id, request)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave request approved", leaveRequestService.approve(id)));
    }

    @PutMapping("/{id}/refuse")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> refuse(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave request refused", leaveRequestService.refuse(id)));
    }

    @PutMapping("/{id}/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> resetToPending(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave request reset to pending", leaveRequestService.resetToPending(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        leaveRequestService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Leave request deleted"));
    }
}
