package com.pointagepro.reports;

import com.pointagepro.reports.dto.ReportResponse;
import com.pointagepro.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
public class ReportsController {

    private final ReportsService service;

    public ReportsController(ReportsService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<ReportResponse>> getMonthly(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {
        if (month == 0) month = java.time.LocalDate.now().getMonthValue();
        if (year == 0) year = java.time.LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success("Monthly report", service.getMonthlyReport(month, year)));
    }

    @GetMapping("/annual")
    public ResponseEntity<ApiResponse<ReportResponse>> getAnnual(
            @RequestParam(defaultValue = "0") int year) {
        if (year == 0) year = java.time.LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success("Annual report", service.getAnnualReport(year)));
    }
}
