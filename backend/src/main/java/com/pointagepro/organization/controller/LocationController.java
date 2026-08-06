package com.pointagepro.organization.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.organization.dto.LocationRequest;
import com.pointagepro.organization.dto.LocationResponse;
import com.pointagepro.organization.service.OrganizationService;
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
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final OrganizationService organizationService;
    private final CurrentUserService currentUserService;

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('location.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> lookup(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des lieux",
                organizationService.locationLookup(company)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('location.read')")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> list(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des lieux",
                organizationService.listLocations(company)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('location.read')")
    public ResponseEntity<ApiResponse<LocationResponse>> get(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Lieu",
                organizationService.getLocation(company, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('location.write')")
    public ResponseEntity<ApiResponse<LocationResponse>> create(
            @AuthenticationPrincipal User user, @Valid @RequestBody LocationRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Lieu créé", organizationService.createLocation(company, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('location.write')")
    public ResponseEntity<ApiResponse<LocationResponse>> update(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody LocationRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Lieu mis à jour",
                organizationService.updateLocation(company, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('location.write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        organizationService.deleteLocation(company, id);
        return ResponseEntity.ok(ApiResponse.success("Lieu supprimé"));
    }
}
