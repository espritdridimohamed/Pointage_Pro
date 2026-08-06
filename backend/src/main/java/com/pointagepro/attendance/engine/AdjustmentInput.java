package com.pointagepro.attendance.engine;

/**
 * An approved attendance adjustment input: typeCode is one of
 * ADD_MINUTES, REMOVE_MINUTES, ADD_OVERTIME, REMOVE_OVERTIME, SET_ABSENT.
 */
public record AdjustmentInput(String typeCode, int minutes) {
}
