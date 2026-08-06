package com.pointagepro.organization.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.organization.dto.PositionRequest;
import com.pointagepro.organization.dto.PositionResponse;
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
@RequestMapping("/positions")
@RequiredArgsConstructor
public class PositionController {

    private final OrganizationService organizationService;
    private final CurrentUserService currentUserService;

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('position.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> lookup(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des postes",
                organizationService.positionLookup(company)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('position.read')")
    public ResponseEntity<ApiResponse<List<PositionResponse>>> list(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des postes",
                organizationService.listPositions(company)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('position.read')")
    public ResponseEntity<ApiResponse<PositionResponse>> get(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Poste",
                organizationService.getPosition(company, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('position.write')")
    public ResponseEntity<ApiResponse<PositionResponse>> create(
            @AuthenticationPrincipal User user, @Valid @RequestBody PositionRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Poste créé", organizationService.createPosition(company, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('position.write')")
    public ResponseEntity<ApiResponse<PositionResponse>> update(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody PositionRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Poste mis à jour",
                organizationService.updatePosition(company, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('position.write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        organizationService.deletePosition(company, id);
        return ResponseEntity.ok(ApiResponse.success("Poste supprimé"));
    }
}
