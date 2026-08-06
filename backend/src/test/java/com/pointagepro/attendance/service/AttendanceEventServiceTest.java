package com.pointagepro.attendance.service;

import com.pointagepro.attendance.dto.AttendanceEventResult;
import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.attendance.entity.EventType;
import com.pointagepro.attendance.repository.AttendanceEventRepository;
import com.pointagepro.attendance.repository.EventTypeRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceEventServiceTest {

    private static final long COMPANY = 1L;
    private static final long TERMINAL = 7L;

    @Mock
    private AttendanceEventRepository eventRepository;
    @Mock
    private EventTypeRepository eventTypeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceEngineService engineService;

    @InjectMocks
    private AttendanceEventService service;

    private static Company company() {
        Company c = new Company();
        c.setId(COMPANY);
        return c;
    }

    private static Employee employee() {
        Employee e = new Employee();
        e.setId(10L);
        e.setCompany(company());
        e.setRfidUid("1A2B3C4D");
        e.setMatricule("EMP-0001");
        e.setFirstName("Ahmed");
        e.setLastName("Ben Salah");
        return e;
    }

    private static Employee employeeOfOtherCompany() {
        Employee e = employee();
        Company other = new Company();
        other.setId(99L);
        e.setCompany(other);
        return e;
    }

    private static EventType type(String code) {
        EventType t = new EventType();
        t.setCode(code);
        return t;
    }

    private static AttendanceEvent eventOf(Employee employee, EventType eventType, LocalDateTime time) {
        AttendanceEvent e = new AttendanceEvent();
        e.setId(500L);
        e.setCompany(employee.getCompany());
        e.setEmployee(employee);
        e.setEventType(eventType);
        e.setEventTime(time);
        e.setRfidUid(employee.getRfidUid());
        e.setSource("TERMINAL");
        return e;
    }

    @Test
    void unknownBadgeReturnsUnknownBadge() {
        when(employeeRepository.findByRfidUid("NOPE")).thenReturn(Optional.empty());

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "NOPE", null, null, "ESP32-001-00000042");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.UNKNOWN_BADGE);
        verify(eventRepository, never()).save(any());
        verify(engineService, never()).recompute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void badgeOfAnotherCompanyIsUnknown() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employeeOfOtherCompany()));

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", null, null, "ESP32-001-00000042");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.UNKNOWN_BADGE);
    }

    @Test
    void replayOfExternalRefIsAbsorbed() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000042"))
                .thenReturn(true);

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", null, null, "ESP32-001-00000042");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.REPLAY);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void duplicateWithinWindowIsAbsorbed() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000042"))
                .thenReturn(false);
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                eq(10L), eq(TERMINAL), any(), any())).thenReturn(Optional.of(new AttendanceEvent()));

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", "2026-08-05T08:00:00", null, "ESP32-001-00000042");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.DUPLICATE);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void typeLessPunchWithNoPriorEventIsIn() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000042"))
                .thenReturn(false);
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                eq(10L), eq(TERMINAL), any(), any())).thenReturn(Optional.empty());
        when(eventRepository.findFirstByEmployeeIdAndEventTimeLessThanOrderByEventTimeDesc(
                eq(10L), any())).thenReturn(Optional.empty());
        when(eventTypeRepository.findByCode("IN")).thenReturn(Optional.of(type("IN")));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", "2026-08-05T08:00:00", null, "ESP32-001-00000042");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.STORED);
        assertThat(result.action()).isEqualTo("IN");
        assertThat(result.employee().getMatricule()).isEqualTo("EMP-0001");
        verify(engineService).recompute(eq(COMPANY), eq(10L), any(), any(), isNull(), anyString());
    }

    @Test
    void typeLessPunchAfterInBecomesOut() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000043"))
                .thenReturn(false);
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                eq(10L), eq(TERMINAL), any(), any())).thenReturn(Optional.empty());
        AttendanceEvent prior = eventOf(employee(), type("IN"), LocalDateTime.of(2026, 8, 5, 8, 0));
        when(eventRepository.findFirstByEmployeeIdAndEventTimeLessThanOrderByEventTimeDesc(
                eq(10L), any())).thenReturn(Optional.of(prior));
        when(eventTypeRepository.findByCode("OUT")).thenReturn(Optional.of(type("OUT")));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", "2026-08-05T17:00:00", null, "ESP32-001-00000043");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.STORED);
        assertThat(result.action()).isEqualTo("OUT");
    }

    @Test
    void explicitTypeOverrideWins() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000044"))
                .thenReturn(false);
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                eq(10L), eq(TERMINAL), any(), any())).thenReturn(Optional.empty());
        when(eventTypeRepository.findByCode("OUT")).thenReturn(Optional.of(type("OUT")));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", "2026-08-05T08:00:00", "OUT", "ESP32-001-00000044");

        assertThat(result.action()).isEqualTo("OUT");
    }

    @Test
    void invalidExplicitTypeRejected() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000045"))
                .thenReturn(false);
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                eq(10L), eq(TERMINAL), any(), any())).thenReturn(Optional.empty());

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", "2026-08-05T08:00:00", "MIDDAY", "ESP32-001-00000045");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.INVALID_TYPE);
    }

    @Test
    void malformedTimestampRejected() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));

        assertThatThrownBy(() -> service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", "08-05-2026", null, "ESP32-001-00000046"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601");
    }

    @Test
    void missingTimestampFallsBackToServerNow() {
        when(employeeRepository.findByRfidUid("1A2B3C4D")).thenReturn(Optional.of(employee()));
        when(eventRepository.existsByTerminalIdAndExternalRef(TERMINAL, "ESP32-001-00000047"))
                .thenReturn(false);
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                eq(10L), eq(TERMINAL), any(), any())).thenReturn(Optional.empty());
        when(eventTypeRepository.findByCode("IN")).thenReturn(Optional.of(type("IN")));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceEventResult result = service.recordPunch(
                company(), TERMINAL, "1A2B3C4D", null, null, "ESP32-001-00000047");

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.STORED);
        assertThat(result.event().getEventTime()).isNotNull();
    }

    @Test
    void recordManualStoresAndRecomputes() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee()));
        when(eventTypeRepository.findByCode("IN")).thenReturn(Optional.of(type("IN")));
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTypeCodeAndEventTimeBetween(
                eq(10L), isNull(), eq("IN"), any(), any())).thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceEventResult result = service.recordManual(
                company(), 10L, "IN", LocalDateTime.of(2026, 8, 5, 8, 0), null);

        assertThat(result.status()).isEqualTo(AttendanceEventResult.Status.STORED);
        assertThat(result.event().getSource()).isEqualTo("MANUAL");
        verify(engineService).recompute(eq(COMPANY), eq(10L), any(), any(), isNull(), anyString());
    }

    @Test
    void recordManualDuplicateThrows() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee()));
        when(eventTypeRepository.findByCode("IN")).thenReturn(Optional.of(type("IN")));
        when(eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTypeCodeAndEventTimeBetween(
                eq(10L), isNull(), eq("IN"), any(), any()))
                .thenReturn(Optional.of(new AttendanceEvent()));

        assertThatThrownBy(() -> service.recordManual(
                company(), 10L, "IN", LocalDateTime.of(2026, 8, 5, 8, 0), null))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void recordManualCrossCompanyEmployeeThrows() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employeeOfOtherCompany()));

        assertThatThrownBy(() -> service.recordManual(
                company(), 10L, "IN", LocalDateTime.of(2026, 8, 5, 8, 0), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForEmployeeCrossCompanyThrows() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employeeOfOtherCompany()));

        assertThatThrownBy(() -> service.listForEmployee(
                company(), 10L, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 5, 23, 59)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
