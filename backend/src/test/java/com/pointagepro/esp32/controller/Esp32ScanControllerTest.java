package com.pointagepro.esp32.controller;

import com.pointagepro.attendance.dto.AttendanceEventResult;
import com.pointagepro.attendance.dto.TerminalScanRequest;
import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.attendance.entity.EventType;
import com.pointagepro.attendance.service.AttendanceEventService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.esp32.service.Esp32ApiKeyService;
import com.pointagepro.shared.exception.GlobalExceptionHandler;
import com.pointagepro.terminal.entity.Terminal;
import com.pointagepro.terminal.entity.TerminalStatus;
import com.pointagepro.terminal.service.TerminalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class Esp32ScanControllerTest {

    private static final String API_KEY = "pointagepro-esp32-device-key-2026";

    @Mock
    private Esp32ApiKeyService apiKeyService;
    @Mock
    private TerminalService terminalService;
    @Mock
    private AttendanceEventService attendanceEventService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Esp32ScanController(apiKeyService, terminalService, attendanceEventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Terminal terminal(Company company, TerminalStatus status) {
        Terminal t = new Terminal();
        t.setId(7L);
        t.setCompany(company);
        t.setSerialNumber("ESP32-001");
        t.setStatus(status);
        return t;
    }

    private static AttendanceEventResult storedIn() {
        Company company = new Company();
        company.setId(1L);
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setCompany(company);
        employee.setFirstName("Ahmed");
        employee.setLastName("Ben Salah");
        employee.setMatricule("EMP-0001");
        AttendanceEvent event = new AttendanceEvent();
        event.setId(500L);
        event.setCompany(company);
        event.setEmployee(employee);
        EventType eventType = new EventType();
        eventType.setCode("IN");
        event.setEventType(eventType);
        event.setEventTime(LocalDateTime.of(2026, 8, 5, 8, 0));
        return AttendanceEventResult.stored(event, employee);
    }

    @Test
    void validPunchStoredReturnsDeviceShape() throws Exception {
        Company company = new Company();
        company.setId(1L);
        TerminalStatus online = new TerminalStatus();
        online.setCode("ONLINE");
        Terminal terminal = terminal(company, online);
        AttendanceEventResult stored = storedIn();

        when(apiKeyService.isValid(API_KEY)).thenReturn(true);
        when(terminalService.resolveSerial(isNull(), eq("ESP32-001-00000042"))).thenReturn("ESP32-001");
        when(terminalService.existsBySerialNumber("ESP32-001")).thenReturn(true);
        when(terminalService.findScanEnabledTerminal(isNull(), eq("ESP32-001-00000042")))
                .thenReturn(Optional.of(terminal));
        when(attendanceEventService.recordPunch(
                eq(company), eq(7L), eq("1A2B3C4D"), eq("2026-08-05T08:00:00"), isNull(),
                eq("ESP32-001-00000042"))).thenReturn(stored);

        mockMvc.perform(post("/esp32/scan")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rfidUid":"1A2B3C4D",
                                 "externalRef":"ESP32-001-00000042",
                                 "timestamp":"2026-08-05T08:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.action").value("IN"))
                .andExpect(jsonPath("$.employeeName").value("Ahmed Ben Salah"))
                .andExpect(jsonPath("$.matricule").value("EMP-0001"))
                .andExpect(jsonPath("$.message").value("Entrée enregistrée"))
                .andExpect(jsonPath("$.time").value("08:00"));
    }

    @Test
    void missingApiKeyReturnsUnauthorized() throws Exception {
        when(apiKeyService.isValid(null)).thenReturn(false);

        mockMvc.perform(post("/esp32/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rfidUid":"1A2B3C4D","externalRef":"ESP32-001-00000042"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Clé API invalide"));
    }

    @Test
    void unknownTerminalReturnsBadRequest() throws Exception {
        when(apiKeyService.isValid(API_KEY)).thenReturn(true);
        when(terminalService.resolveSerial(any(), any())).thenReturn(null);

        mockMvc.perform(post("/esp32/scan")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rfidUid\":\"1A2B3C4D\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Terminal inconnu"));
    }

    @Test
    void disabledTerminalReturnsForbidden() throws Exception {
        Company company = new Company();
        company.setId(1L);
        when(apiKeyService.isValid(API_KEY)).thenReturn(true);
        when(terminalService.resolveSerial(isNull(), eq("ESP32-001-00000042"))).thenReturn("ESP32-001");
        when(terminalService.existsBySerialNumber("ESP32-001")).thenReturn(true);
        when(terminalService.findScanEnabledTerminal(isNull(), eq("ESP32-001-00000042")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/esp32/scan")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rfidUid":"1A2B3C4D",
                                 "externalRef":"ESP32-001-00000042",
                                 "timestamp":"2026-08-05T08:00:00"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Terminal inactif"));
    }

    @Test
    void unknownBadgeReturnsSuccessFalse() throws Exception {
        Company company = new Company();
        company.setId(1L);
        TerminalStatus online = new TerminalStatus();
        online.setCode("ONLINE");
        Terminal terminal = terminal(company, online);

        when(apiKeyService.isValid(API_KEY)).thenReturn(true);
        when(terminalService.resolveSerial(isNull(), eq("ESP32-001-00000042"))).thenReturn("ESP32-001");
        when(terminalService.existsBySerialNumber("ESP32-001")).thenReturn(true);
        when(terminalService.findScanEnabledTerminal(isNull(), eq("ESP32-001-00000042")))
                .thenReturn(Optional.of(terminal));
        when(attendanceEventService.recordPunch(
                eq(company), eq(7L), eq("UNKNOWN"), isNull(), isNull(), eq("ESP32-001-00000042")))
                .thenReturn(AttendanceEventResult.unknownBadge());

        mockMvc.perform(post("/esp32/scan")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rfidUid":"UNKNOWN","externalRef":"ESP32-001-00000042"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.action").value(""))
                .andExpect(jsonPath("$.message").value("Badge non reconnu"));
    }

    @Test
    void malformedTimestampReturnsBadRequest() throws Exception {
        Company company = new Company();
        company.setId(1L);
        TerminalStatus online = new TerminalStatus();
        online.setCode("ONLINE");
        Terminal terminal = terminal(company, online);

        when(apiKeyService.isValid(API_KEY)).thenReturn(true);
        when(terminalService.resolveSerial(isNull(), eq("ESP32-001-00000042"))).thenReturn("ESP32-001");
        when(terminalService.existsBySerialNumber("ESP32-001")).thenReturn(true);
        when(terminalService.findScanEnabledTerminal(isNull(), eq("ESP32-001-00000042")))
                .thenReturn(Optional.of(terminal));
        when(attendanceEventService.recordPunch(
                eq(company), eq(7L), eq("1A2B3C4D"), eq("08-05-2026"), isNull(), eq("ESP32-001-00000042")))
                .thenThrow(new IllegalArgumentException("timestamp must be ISO-8601 yyyy-MM-dd'T'HH:mm:ss, got: 08-05-2026"));

        mockMvc.perform(post("/esp32/scan")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rfidUid":"1A2B3C4D",
                                 "externalRef":"ESP32-001-00000042",
                                 "timestamp":"08-05-2026"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidBodyRejectedByValidation() throws Exception {
        mockMvc.perform(post("/esp32/scan")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalRef\":\"ESP32-001-00000042\"}"))
                .andExpect(status().isBadRequest());
    }
}
