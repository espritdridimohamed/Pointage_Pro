package com.pointagepro.terminal.service;

import com.pointagepro.terminal.entity.Terminal;
import com.pointagepro.terminal.repository.TerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Terminal resolution + scan-gating for the ESP32 flow. The scan body carries no device
 * identifier, so the serial is taken from an explicit deviceSerial field or derived from
 * externalRef (firmware format: {@code <deviceSerial>-<sequence>}, e.g. ESP32-001-00000042).
 */
@Service
@RequiredArgsConstructor
public class TerminalService {

    private static final Pattern EXTERNAL_REF_SERIAL = Pattern.compile("^(.*)-[0-9]+$");

    private final TerminalRepository terminalRepository;

    /**
     * Returns the terminal eligible for scans, or empty when the serial is unknown or the
     * terminal is MAINTENANCE / DISABLED. OFFline/ONLINE both accept punches (OFFLINE may
     * replay buffered scans).
     */
    @Transactional(readOnly = true)
    public Optional<Terminal> findScanEnabledTerminal(String deviceSerial, String externalRef) {
        String serial = resolveSerial(deviceSerial, externalRef);
        if (serial == null) {
            return Optional.empty();
        }
        Optional<Terminal> terminal = terminalRepository.findBySerialNumber(serial);
        if (terminal.isEmpty() || !isScanEnabled(terminal.get())) {
            return Optional.empty();
        }
        terminal.get().getStatus().getCode();
        terminal.get().getCompany().getId();
        return terminal;
    }

    public boolean existsBySerialNumber(String serial) {
        return serial != null && terminalRepository.existsBySerialNumber(serial);
    }

    public String resolveSerial(String deviceSerial, String externalRef) {
        if (deviceSerial != null && !deviceSerial.isBlank()) {
            return deviceSerial.trim();
        }
        if (externalRef == null) {
            return null;
        }
        Matcher matcher = EXTERNAL_REF_SERIAL.matcher(externalRef);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean isScanEnabled(Terminal terminal) {
        if (terminal.getStatus() == null) {
            return false;
        }
        String code = terminal.getStatus().getCode();
        return !"MAINTENANCE".equals(code) && !"DISABLED".equals(code);
    }
}
