package com.pointagepro.attendance.engine;

/**
 * A raw attendance event expressed in minutes since the work date's 00:00.
 * Post-midnight night-shift events use minuteOfDay >= 1440 (e.g. 06:00 of D+1 = 1800).
 */
public record EventInput(String typeCode, int minuteOfDay) {
}
