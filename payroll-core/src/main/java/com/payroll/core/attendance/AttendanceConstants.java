package com.payroll.core.attendance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class AttendanceConstants {

    private AttendanceConstants() {}

    public static final LocalTime FLEX_ARRIVAL_START    = LocalTime.of(7, 0);
    public static final LocalTime FLEX_ARRIVAL_END      = LocalTime.of(8, 0);
    public static final LocalTime SHIFT_START_BOUNDARY  = LocalTime.of(7, 30);

    public static final Duration  STANDARD_DAY_LENGTH   = Duration.ofHours(9);

    public static final LocalTime     AM_HALF_ARRIVAL_START  = LocalTime.of(7, 0);
    public static final LocalTime     AM_HALF_ARRIVAL_END    = LocalTime.of(8, 0);
    public static final Duration      AM_HALF_DURATION       = Duration.ofHours(4).plusMinutes(15);

    public static final LocalTime     PM_HALF_START_EARLIEST = LocalTime.of(11, 15);
    public static final LocalTime     PM_HALF_START_LATEST   = LocalTime.of(11, 45);
    public static final Duration      PM_HALF_DURATION       = Duration.ofHours(4).plusMinutes(45);

    public static final LocalTime SATURDAY_START = LocalTime.of(7, 25);
    public static final LocalTime SATURDAY_END   = LocalTime.of(13, 20);

    public static final LocalTime PEELING_ARRIVAL = LocalTime.of(8, 0);
    public static final LocalTime PEELING_LEAVE   = LocalTime.of(16, 30);

    public static LocalDateTime shiftEnd(LocalDateTime arrival) {
        return arrival.plus(STANDARD_DAY_LENGTH);
    }
}
