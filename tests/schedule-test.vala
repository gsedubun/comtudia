using Comtudia;

void assert_int (int actual, int expected, string label) {
    if (actual != expected) {
        GLib.error ("%s: got %d, want %d", label, actual, expected);
    }
}

void assert_span (GLib.TimeSpan actual, GLib.TimeSpan expected, string label) {
    if (actual != expected) {
        GLib.error ("%s: got %lld, want %lld", label, actual, expected);
    }
}

void assert_dt (GLib.DateTime? actual, GLib.DateTime? expected, string label) {
    if (actual == null || expected == null) {
        if (actual != expected) {
            GLib.error ("%s: one value is null", label);
        }
        return;
    }
    if (!actual.equal (expected)) {
        GLib.error ("%s: got %s, want %s", label,
            actual.format ("%Y-%m-%d %H:%M:%S %Z"),
            expected.format ("%Y-%m-%d %H:%M:%S %Z"));
    }
}

void assert_progress (double actual, double expected, string label) {
    if ((actual - expected).abs () > 1e-9) {
        GLib.error ("%s: got %f, want %f", label, actual, expected);
    }
}

GLib.DateTime utc (int year, int month, int day, int hour, int minute) {
    return new GLib.DateTime.utc (year, month, day, hour, minute, 0);
}

GLib.DateTime new_york (int year, int month, int day, int hour, int minute)
        throws GLib.Error {
    var tz = new GLib.TimeZone.identifier ("America/New_York");
    return new GLib.DateTime (tz, year, month, day, hour, minute, 0);
}

State state_at (Plan plan, GLib.DateTime now) {
    try {
        return Schedule.compute (plan, now);
    } catch (PlanError e) {
        GLib.error ("Compute: %s", e.message);
    }
    return new State (now);
}

void test_contiguous_plan () {
    var plan = Plan.create_default ();
    var day = utc (2026, 10, 26, 0, 0);

    var state = state_at (plan, day.add_hours (10));
    GLib.assert_true (state.phase == Phase.FASTING);
    assert_dt (state.phase_start, day.add_days (-1).add_hours (18), "phase start");
    assert_dt (state.phase_end, day.add_hours (12), "phase end");
    assert_span (state.remaining, 2 * GLib.TimeSpan.HOUR, "remaining");
    assert_progress (state.progress, 16.0 / 18.0, "progress");
    assert_dt (state.next_meal.start, day.add_hours (12), "next meal");
    assert_dt (state.next_window_start, day.add_hours (12), "next window");

    state = state_at (plan, day.add_hours (12));
    GLib.assert_true (state.phase == Phase.EATING);
    GLib.assert_true (state.current_meal != null);
    assert_int (state.current_meal.index, 1, "current meal index");
    assert_dt (state.phase_start, day.add_hours (12), "window phase start");
    assert_dt (state.phase_end, day.add_hours (18), "window phase end");
    assert_int (state.first_meal_index, 1, "first meal index");
    assert_int (state.last_meal_index, 3, "last meal index");
    assert_span (state.remaining, 6 * GLib.TimeSpan.HOUR, "window remaining");
    assert_progress (state.progress, 0, "window progress");
    assert_dt (state.next_meal.start, day.add_hours (14), "next meal at window");
    assert_dt (state.next_eating_phase_start,
        day.add_days (1).add_hours (12), "next eating phase");
    assert_dt (state.next_window_start,
        day.add_days (1).add_hours (12), "next window at start");

    state = state_at (plan, day.add_hours (14));
    GLib.assert_true (state.phase == Phase.EATING);
    GLib.assert_true (state.current_meal != null);
    assert_int (state.current_meal.index, 2, "middle meal index");
    assert_progress (state.progress, 1.0 / 3.0, "middle progress");

    state = state_at (plan, day.add_hours (18));
    GLib.assert_true (state.phase == Phase.FASTING);
    assert_dt (state.phase_start, day.add_hours (18), "close phase start");
    assert_dt (state.next_meal.start,
        day.add_days (1).add_hours (12), "close next meal");
    assert_progress (state.progress, 0, "close progress");

    state = state_at (plan, day.add_hours (18).add_seconds (-1));
    GLib.assert_true (state.phase == Phase.EATING);
    assert_span (state.remaining, GLib.TimeSpan.SECOND, "one second remaining");
}

void test_with_gaps () {
    var plan = Plan.create_default ();
    plan.gap_minutes = 60;
    var day = utc (2026, 10, 26, 0, 0);

    var state = state_at (plan, day.add_hours (12));
    GLib.assert_true (state.phase == Phase.EATING);
    GLib.assert_true (state.current_meal != null);
    assert_int (state.current_meal.index, 1, "gap meal one");
    assert_int (state.first_meal_index, 1, "gap first index");
    assert_int (state.last_meal_index, 1, "gap last index");
    assert_dt (state.next_eating_phase_start,
        day.add_hours (15), "gap next eating phase");

    state = state_at (plan, day.add_hours (14).add_minutes (30));
    GLib.assert_true (state.phase == Phase.FASTING);
    assert_dt (state.phase_start, day.add_hours (14), "gap phase start");
    assert_dt (state.phase_end, day.add_hours (15), "gap phase end");
    assert_span (state.remaining, 30 * GLib.TimeSpan.MINUTE, "gap remaining");
    assert_progress (state.progress, 0.5, "gap progress");
    assert_dt (state.next_meal.start, day.add_hours (15), "gap next meal");
    assert_int (state.first_meal_index, 2, "gap next first index");
    assert_int (state.last_meal_index, 2, "gap next last index");

    state = state_at (plan, day.add_hours (15));
    GLib.assert_true (state.phase == Phase.EATING);
    GLib.assert_true (state.current_meal != null);
    assert_int (state.current_meal.index, 2, "gap meal two");
    assert_dt (state.next_eating_phase_start,
        day.add_hours (18), "gap meal two next phase");

    state = state_at (plan, day.add_hours (20));
    GLib.assert_true (state.phase == Phase.FASTING);
    assert_dt (state.phase_start, day.add_hours (20), "after gap phase start");
    assert_span (state.long_fast, 16 * GLib.TimeSpan.HOUR, "after gap long fast");
    assert_span (state.fasting_total, 18 * GLib.TimeSpan.HOUR, "after gap fasting total");
}

void test_window_crossing_midnight () {
    var plan = Plan.create_default ();
    plan.window_start_minutes = 23 * 60;
    var day = utc (2026, 10, 26, 0, 0);

    var state = state_at (plan, day.add_days (1).add_minutes (30));
    GLib.assert_true (state.phase == Phase.EATING);
    GLib.assert_true (state.current_meal != null);
    assert_int (state.current_meal.index, 1, "midnight meal index");
    assert_dt (state.current_meal.day, day, "midnight meal day");
    assert_dt (state.phase_start, day.add_hours (23), "midnight phase start");
    assert_dt (state.phase_end,
        day.add_days (1).add_hours (5), "midnight phase end");
    assert_dt (state.next_window_start,
        day.add_days (1).add_hours (23), "midnight next window");

    state = state_at (plan, day.add_days (1).add_hours (6));
    GLib.assert_true (state.phase == Phase.FASTING);
    assert_dt (state.next_meal.start,
        day.add_days (1).add_hours (23), "morning next meal");
    assert_dt (state.next_window_start,
        day.add_days (1).add_hours (23), "morning next window");
}

void test_slots_across_dst () {
    GLib.DateTime day;
    try {
        day = new_york (2026, 3, 8, 12, 0);
    } catch (GLib.Error e) {
        return;
    }

    var plan = Plan.create_default ();
    plan.window_start_minutes = 23 * 60;
    var slots = Schedule.slots_for_date (plan, day);
    assert_int (slots.length, 3, "DST slot count");
    string[] want_starts = { "23:00", "01:00", "03:00" };
    string[] want_ends = { "01:00", "03:00", "05:00" };
    for (int i = 0; i < slots.length; i++) {
        GLib.assert_cmpstr (Format.clock (slots[i].start),
            GLib.CompareOperator.EQ, want_starts[i]);
        GLib.assert_cmpstr (Format.clock (slots[i].end),
            GLib.CompareOperator.EQ, want_ends[i]);
        GLib.assert_true (slots[i].end.compare (slots[i].start) > 0);
        if (i > 0) {
            GLib.assert_true (slots[i].start.compare (slots[i - 1].end) >= 0);
        }
    }
}

void test_slots_across_spring_forward () {
    GLib.DateTime day;
    try {
        day = new_york (2026, 3, 8, 12, 0);
    } catch (GLib.Error e) {
        return;
    }

    var plan = new Plan ();
    plan.eating_minutes = 120;
    plan.meals = 1;
    plan.window_start_minutes = 90;
    var slots = Schedule.slots_for_date (plan, day);
    assert_int (slots.length, 1, "spring slot count");
    GLib.assert_cmpstr (Format.clock (slots[0].start),
        GLib.CompareOperator.EQ, "01:30");
    GLib.assert_cmpstr (Format.clock (slots[0].end),
        GLib.CompareOperator.EQ, "04:30");
    assert_span (slots[0].duration (), 2 * GLib.TimeSpan.HOUR,
        "spring slot duration");
}

void test_compute_invalid_plan () {
    var plan = new Plan ();
    plan.meals = 0;
    try {
        Schedule.compute (plan, new GLib.DateTime.now_local ());
        GLib.error ("expected validation error");
    } catch (PlanError e) {
        GLib.assert_true (e.message != null);
    }
}

void test_compute_across_spring_forward () {
    GLib.DateTime now;
    GLib.DateTime phase_start;
    GLib.DateTime phase_end;
    try {
        now = new_york (2026, 3, 8, 3, 30);
        phase_start = new_york (2026, 3, 8, 1, 30);
        phase_end = new_york (2026, 3, 8, 4, 30);
    } catch (GLib.Error e) {
        return;
    }

    var plan = new Plan ();
    plan.eating_minutes = 120;
    plan.meals = 1;
    plan.window_start_minutes = 90;
    var state = state_at (plan, now);
    GLib.assert_true (state.phase == Phase.EATING);
    GLib.assert_true (state.current_meal != null);
    assert_int (state.current_meal.index, 1, "spring current meal");
    assert_dt (state.phase_start, phase_start, "spring phase start");
    assert_dt (state.phase_end, phase_end, "spring phase end");
    assert_span (state.phase_end.difference (state.phase_start),
        2 * GLib.TimeSpan.HOUR, "spring phase duration");
    assert_span (state.remaining, GLib.TimeSpan.HOUR, "spring remaining");
    assert_progress (state.progress, 0.5, "spring progress");
}

void test_slots_with_gap_across_spring_forward () {
    GLib.DateTime day;
    try {
        day = new_york (2026, 3, 8, 12, 0);
    } catch (GLib.Error e) {
        return;
    }

    var plan = new Plan ();
    plan.eating_minutes = 120;
    plan.meals = 2;
    plan.gap_minutes = 30;
    plan.window_start_minutes = 90;
    var slots = Schedule.slots_for_date (plan, day);
    assert_int (slots.length, 2, "spring gap slot count");
    for (int i = 0; i < slots.length; i++) {
        assert_span (slots[i].duration (), GLib.TimeSpan.HOUR,
            "spring gap slot duration");
    }
    assert_span (slots[1].start.difference (slots[0].end),
        30 * GLib.TimeSpan.MINUTE, "spring gap size");
    GLib.assert_true (slots[1].start.compare (slots[0].end) >= 0);
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/schedule/contiguous", test_contiguous_plan);
    GLib.Test.add_func ("/schedule/gaps", test_with_gaps);
    GLib.Test.add_func ("/schedule/midnight", test_window_crossing_midnight);
    GLib.Test.add_func ("/schedule/dst-slots", test_slots_across_dst);
    GLib.Test.add_func ("/schedule/spring-forward", test_slots_across_spring_forward);
    GLib.Test.add_func ("/schedule/invalid", test_compute_invalid_plan);
    GLib.Test.add_func ("/schedule/compute-spring-forward",
        test_compute_across_spring_forward);
    GLib.Test.add_func ("/schedule/gap-spring-forward",
        test_slots_with_gap_across_spring_forward);
    return GLib.Test.run ();
}
