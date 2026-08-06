package com.pointagepro.esp32.controller;

import com.pointagepro.attendance.dto.AttendanceEventResult;
import com.pointagepro.attendance.dto.TerminalScanRequest;
import com.pointagepro.attendance.dto.TerminalScanResponse;
import com.pointagepro.attendance.service.AttendanceEventService;
import com.pointagepro.esp32.service.Esp32ApiKeyService;
import com.pointagepro.terminal.entity.Terminal;
import com.pointagepro.terminal.service.TerminalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device endpoint for the ESP32 badge readers. Authenticated by the shared API key
 * (X-API-Key), never by JWT (see SecurityConfig: /esp32/** is whitelisted). Returns a flat
 * firmware-shaped body (firmware/src/api_client.cpp), not the ApiResponse envelope.
 */
@RestController
@RequestMapping("/esp32")
@RequiredArgsConstructor
public class Esp32ScanController {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final Esp32ApiKeyService apiKeyService;
    private final TerminalService terminalService;
    private final AttendanceEventService attendanceEventService;

    @PostMapping("/scan")
    public ResponseEntity<TerminalScanResponse> scan(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody TerminalScanRequest request) {

        if (!apiKeyService.isValid(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(TerminalScanResponse.rejected("Clé API invalide"));
        }

        String serial = terminalService.resolveSerial(request.getDeviceSerial(), request.getExternalRef());
        if (serial == null) {
            return ResponseEntity.badRequest()
                    .body(TerminalScanResponse.rejected("Terminal inconnu"));
        }
        if (!terminalService.existsBySerialNumber(serial)) {
            return ResponseEntity.badRequest()
                    .body(TerminalScanResponse.rejected("Terminal inconnu"));
        }
        Terminal terminal = terminalService
                .findScanEnabledTerminal(request.getDeviceSerial(), request.getExternalRef())
                .orElse(null);
        if (terminal == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(TerminalScanResponse.rejected("Terminal inactif"));
        }

        AttendanceEventResult result = attendanceEventService.recordPunch(
                terminal.getCompany(), terminal.getId(), request.getRfidUid(),
                request.getTimestamp(), request.getEventType(), request.getExternalRef());

        return ResponseEntity.ok(toDeviceResponse(result));
    }

    private TerminalScanResponse toDeviceResponse(AttendanceEventResult result) {
        return switch (result.status()) {
            case STORED -> TerminalScanResponse.success(
                    result.action(),
                    result.employee().getFirstName() + " " + result.employee().getLastName(),
                    result.employee().getMatricule(),
                    "IN".equals(result.action()) ? "Entrée enregistrée" : "Sortie enregistrée",
                    result.time());
            case DUPLICATE -> TerminalScanResponse.rejected("Scan dupliqué (doublon)");
            case REPLAY -> TerminalScanResponse.rejected("Scan déjà reçu");
            case INVALID_TYPE -> TerminalScanResponse.rejected("Type d'événement invalide");
            case UNKNOWN_BADGE -> TerminalScanResponse.rejected("Badge non reconnu");
        };
    }
}
