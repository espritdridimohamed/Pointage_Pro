package com.pointagepro.settings;

import com.pointagepro.shared.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings")
public class CompanySettingsController {

    private final CompanySettingsService service;

    public CompanySettingsController(CompanySettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CompanySettings>> get() {
        return ResponseEntity.ok(ApiResponse.success("Settings retrieved", service.get()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanySettings>> update(@Valid @RequestBody CompanySettings updates) {
        return ResponseEntity.ok(ApiResponse.success("Settings updated", service.update(updates)));
    }
}
