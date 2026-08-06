package com.pointagepro.shared.exception;

/**
 * Maps to HTTP 409 Conflict. Used for state conflicts: a request that is no longer
 * actionable (already decided / terminal) or an adjustment targeting a frozen payroll
 * period (business rules §5.7).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
