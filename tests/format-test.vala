using Comtudia;

void test_countdown () {
    GLib.assert_cmpstr (Format.countdown (-1 * GLib.TimeSpan.SECOND),
        GLib.CompareOperator.EQ, "00:00:00");
    GLib.assert_cmpstr (Format.countdown (0),
        GLib.CompareOperator.EQ, "00:00:00");
    GLib.assert_cmpstr (Format.countdown (GLib.TimeSpan.SECOND),
        GLib.CompareOperator.EQ, "00:00:01");
    GLib.assert_cmpstr (Format.countdown (59 * GLib.TimeSpan.MINUTE +
        59 * GLib.TimeSpan.SECOND), GLib.CompareOperator.EQ, "00:59:59");
    GLib.assert_cmpstr (Format.countdown (17 * GLib.TimeSpan.HOUR +
        42 * GLib.TimeSpan.MINUTE + 10 * GLib.TimeSpan.SECOND),
        GLib.CompareOperator.EQ, "17:42:10");
    GLib.assert_cmpstr (Format.countdown (23 * GLib.TimeSpan.HOUR +
        59 * GLib.TimeSpan.MINUTE + 59 * GLib.TimeSpan.SECOND),
        GLib.CompareOperator.EQ, "23:59:59");
    GLib.assert_cmpstr (Format.countdown (17 * GLib.TimeSpan.HOUR +
        42 * GLib.TimeSpan.MINUTE + 10 * GLib.TimeSpan.SECOND +
        900 * GLib.TimeSpan.MILLISECOND),
        GLib.CompareOperator.EQ, "17:42:10");
}

void test_duration () {
    GLib.assert_cmpstr (Format.duration (-1 * GLib.TimeSpan.MINUTE),
        GLib.CompareOperator.EQ, "0m");
    GLib.assert_cmpstr (Format.duration (0),
        GLib.CompareOperator.EQ, "0m");
    GLib.assert_cmpstr (Format.duration (30 * GLib.TimeSpan.SECOND),
        GLib.CompareOperator.EQ, "1m");
    GLib.assert_cmpstr (Format.duration (GLib.TimeSpan.MINUTE),
        GLib.CompareOperator.EQ, "1m");
    GLib.assert_cmpstr (Format.duration (59 * GLib.TimeSpan.MINUTE),
        GLib.CompareOperator.EQ, "59m");
    GLib.assert_cmpstr (Format.duration (GLib.TimeSpan.HOUR),
        GLib.CompareOperator.EQ, "1h");
    GLib.assert_cmpstr (Format.duration (GLib.TimeSpan.HOUR +
        30 * GLib.TimeSpan.MINUTE), GLib.CompareOperator.EQ, "1h 30m");
    GLib.assert_cmpstr (Format.duration (18 * GLib.TimeSpan.HOUR),
        GLib.CompareOperator.EQ, "18h");
    GLib.assert_cmpstr (Format.duration (17 * GLib.TimeSpan.HOUR +
        42 * GLib.TimeSpan.MINUTE + 30 * GLib.TimeSpan.SECOND),
        GLib.CompareOperator.EQ, "17h 43m");
}

void test_duration_compact () {
    GLib.assert_cmpstr (Format.duration_compact (0),
        GLib.CompareOperator.EQ, "0m");
    GLib.assert_cmpstr (Format.duration_compact (45 * GLib.TimeSpan.MINUTE),
        GLib.CompareOperator.EQ, "45m");
    GLib.assert_cmpstr (Format.duration_compact (GLib.TimeSpan.HOUR),
        GLib.CompareOperator.EQ, "1h");
    GLib.assert_cmpstr (Format.duration_compact (90 * GLib.TimeSpan.MINUTE),
        GLib.CompareOperator.EQ, "1h 30m");
    GLib.assert_cmpstr (Format.duration_compact (2 * GLib.TimeSpan.HOUR),
        GLib.CompareOperator.EQ, "2h");
}

void test_clock_minutes () {
    GLib.assert_cmpstr (Format.clock_minutes (0),
        GLib.CompareOperator.EQ, "00:00");
    GLib.assert_cmpstr (Format.clock_minutes (90),
        GLib.CompareOperator.EQ, "01:30");
    GLib.assert_cmpstr (Format.clock_minutes (750),
        GLib.CompareOperator.EQ, "12:30");
    GLib.assert_cmpstr (Format.clock_minutes (1439),
        GLib.CompareOperator.EQ, "23:59");
    GLib.assert_cmpstr (Format.clock_minutes (1440),
        GLib.CompareOperator.EQ, "00:00");
    GLib.assert_cmpstr (Format.clock_minutes (1500),
        GLib.CompareOperator.EQ, "01:00");
    GLib.assert_cmpstr (Format.clock_minutes (-60),
        GLib.CompareOperator.EQ, "23:00");
}

void test_ranges () {
    var start = new GLib.DateTime.utc (2026, 10, 26, 12, 0, 0);
    var end = new GLib.DateTime.utc (2026, 10, 26, 14, 30, 0);
    GLib.assert_cmpstr (Format.time_range (start, end),
        GLib.CompareOperator.EQ, "12:00–14:30");

    var next = new GLib.DateTime.utc (2026, 10, 27, 1, 0, 0);
    GLib.assert_cmpstr (Format.time_range (start, next),
        GLib.CompareOperator.EQ, "12:00–01:00 (+1)");

    GLib.assert_cmpstr (Format.range_minutes (12 * 60, 14 * 60),
        GLib.CompareOperator.EQ, "12:00–14:00");
    GLib.assert_cmpstr (Format.range_minutes (23 * 60, 25 * 60),
        GLib.CompareOperator.EQ, "23:00–01:00 (+1)");
}

void test_day_clock_and_when () {
    var now = new GLib.DateTime.utc (2026, 10, 26, 10, 0, 0);
    var today = new GLib.DateTime.utc (2026, 10, 26, 12, 0, 0);
    var tomorrow = new GLib.DateTime.utc (2026, 10, 27, 12, 0, 0);
    var yesterday = new GLib.DateTime.utc (2026, 10, 25, 18, 0, 0);
    var later = new GLib.DateTime.utc (2026, 10, 29, 8, 30, 0);

    GLib.assert_cmpstr (Format.day_clock (today, now),
        GLib.CompareOperator.EQ, "Today 12:00");
    GLib.assert_cmpstr (Format.day_clock (tomorrow, now),
        GLib.CompareOperator.EQ, "Tomorrow 12:00");
    GLib.assert_cmpstr (Format.day_clock (yesterday, now),
        GLib.CompareOperator.EQ, "Yesterday 18:00");
    GLib.assert_cmpstr (Format.day_clock (later, now),
        GLib.CompareOperator.EQ, "Thu 08:30");
    GLib.assert_cmpstr (Format.when (tomorrow, now),
        GLib.CompareOperator.EQ, "tomorrow at 12:00");
    GLib.assert_cmpstr (Format.when (today, now),
        GLib.CompareOperator.EQ, "today at 12:00");
}

void test_day_name_and_same_date () {
    var now = new GLib.DateTime.utc (2026, 10, 26, 10, 0, 0);
    GLib.assert_true (Format.same_date (now,
        new GLib.DateTime.utc (2026, 10, 26, 23, 0, 0)));
    GLib.assert_true (!Format.same_date (now,
        new GLib.DateTime.utc (2026, 10, 27, 0, 0, 0)));
    GLib.assert_cmpstr (Format.day_name (
        new GLib.DateTime.utc (2026, 10, 26, 12, 0, 0), now),
        GLib.CompareOperator.EQ, "Today");
    GLib.assert_cmpstr (Format.day_name (
        new GLib.DateTime.utc (2026, 10, 27, 12, 0, 0), now),
        GLib.CompareOperator.EQ, "Tomorrow");
    GLib.assert_cmpstr (Format.day_name (
        new GLib.DateTime.utc (2026, 10, 25, 12, 0, 0), now),
        GLib.CompareOperator.EQ, "Yesterday");
    GLib.assert_cmpstr (Format.day_name (
        new GLib.DateTime.utc (2026, 10, 29, 12, 0, 0), now),
        GLib.CompareOperator.EQ, "Thursday");
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/format/countdown", test_countdown);
    GLib.Test.add_func ("/format/duration", test_duration);
    GLib.Test.add_func ("/format/duration-compact", test_duration_compact);
    GLib.Test.add_func ("/format/clock-minutes", test_clock_minutes);
    GLib.Test.add_func ("/format/ranges", test_ranges);
    GLib.Test.add_func ("/format/day-clock", test_day_clock_and_when);
    GLib.Test.add_func ("/format/day-name", test_day_name_and_same_date);
    return GLib.Test.run ();
}
