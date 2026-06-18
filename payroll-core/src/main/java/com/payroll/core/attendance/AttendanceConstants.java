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

    public static final LocalTime WEEKDAY_FIRST_HALF_START  = LocalTime.of(7, 25);
    public static final LocalTime WEEKDAY_FIRST_HALF_END    = LocalTime.of(11, 45);
    public static final LocalTime WEEKDAY_SECOND_HALF_START = LocalTime.of(11, 15);
    public static final LocalTime WEEKDAY_SECOND_HALF_END   = LocalTime.of(16, 25);

    public static final LocalTime SATURDAY_START = LocalTime.of(7, 25);
    public static final LocalTime SATURDAY_END   = LocalTime.of(13, 20);

    public static final LocalTime PEELING_ARRIVAL = LocalTime.of(8, 0);
    public static final LocalTime PEELING_LEAVE   = LocalTime.of(16, 30);

    public static LocalDateTime shiftEnd(LocalDateTime arrival) {
        return arrival.plus(STANDARD_DAY_LENGTH);
    }
}
