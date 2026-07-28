package com.pointagepro.dashboard;

import com.pointagepro.dashboard.dto.DashboardChart;
import com.pointagepro.dashboard.dto.DashboardStats;
import com.pointagepro.dashboard.dto.RecentAttendance;
import com.pointagepro.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getStats() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", service.getStats()));
    }

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<DashboardChart>> getChart() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard chart", service.getWeeklyChart()));
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<RecentAttendance>>> getRecent() {
        return ResponseEntity.ok(ApiResponse.success("Recent attendance", service.getRecentAttendance()));
    }
}
