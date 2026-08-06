package com.pointagepro.attendance.engine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DayResult {

    private String statusCode;
    private int workedMinutes;
    /** Worked minutes before the {@code MAX_DAILY_WORKED_MINUTES} clamp, so dry-runs can reject over-cap corrections. */
    private int rawWorkedMinutes;
    private int missingMinutes;
    private int lateMinutes;
    private int earlyExitMinutes;
    private int overtimeMinutes;
    private Integer firstInMinute;
    private Integer lastOutMinute;
    private int adjustmentMinutes;
}
