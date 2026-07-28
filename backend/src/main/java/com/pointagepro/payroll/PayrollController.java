package com.pointagepro.payroll;

import com.pointagepro.payroll.dto.PayrollItemResponse;
import com.pointagepro.payroll.dto.PayrollItemUpdate;
import com.pointagepro.payroll.dto.PayrollResponse;
import com.pointagepro.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payrolls")
public class PayrollController {

    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<PayrollResponse>> generate(
            @RequestParam int month, @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success("Payroll generated", service.generate(month, year)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PayrollResponse>> getByMonth(
            @RequestParam int month, @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success("Payroll retrieved", service.getByMonth(month, year)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayrollResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Payroll retrieved", service.getById(id)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<PayrollItemResponse>> updateItem(
            @PathVariable Long itemId, @RequestBody PayrollItemUpdate update) {
        return ResponseEntity.ok(ApiResponse.success("Item updated", service.updateItem(itemId, update)));
    }

    @PostMapping("/items/{itemId}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayrollItemResponse>> payItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("Item marked as paid", service.payItem(itemId)));
    }

    @PostMapping("/{id}/pay-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayrollResponse>> payAll(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("All items marked as paid", service.payAll(id)));
    }
}
