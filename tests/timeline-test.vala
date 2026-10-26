using Comtudia;

void assert_dt (GLib.DateTime? actual, GLib.DateTime? expected, string label) {
    if (actual == null || expected == null) {
        if (actual != expected) {
            GLib.error ("%s: one value is null", label);
        }
        return;
    }
    if (!actual.equal (expected)) {
        GLib.error ("%s: got %s, want %s", label,
            actual.format ("%Y-%m-%d %H:%M:%S"),
            expected.format ("%Y-%m-%d %H:%M:%S"));
    }
}

void test_segments () {
    var plan = Plan.create_default ();
    var now = new GLib.DateTime.utc (2026, 10, 26, 10, 0, 0);
    GLib.DateTime day_start;
    GLib.DateTime day_end;
    var segments = Timeline.segments_for (
        plan, now, out day_start, out day_end);
    assert_dt (day_start, new GLib.DateTime.utc (2026, 10, 26, 0, 0, 0),
        "day start");
    assert_dt (day_end, new GLib.DateTime.utc (2026, 10, 27, 0, 0, 0),
        "day end");
    if (segments.length != 3) {
        GLib.error ("got %d segments, want 3", segments.length);
    }

    GLib.DateTime[] want_start = {
        new GLib.DateTime.utc (2026, 10, 26, 12, 0, 0),
        new GLib.DateTime.utc (2026, 10, 26, 14, 0, 0),
        new GLib.DateTime.utc (2026, 10, 26, 16, 0, 0)
    };
    GLib.DateTime[] want_end = {
        new GLib.DateTime.utc (2026, 10, 26, 14, 0, 0),
        new GLib.DateTime.utc (2026, 10, 26, 16, 0, 0),
        new GLib.DateTime.utc (2026, 10, 26, 18, 0, 0)
    };
    for (int i = 0; i < segments.length; i++) {
        assert_dt (segments[i].start, want_start[i], "segment start");
        assert_dt (segments[i].end, want_end[i], "segment end");
    }
}

void test_segments_crossing_midnight () {
    var plan = Plan.create_default ();
    plan.window_start_minutes = 23 * 60;
    var now = new GLib.DateTime.utc (2026, 10, 27, 0, 30, 0);
    GLib.DateTime day_start;
    GLib.DateTime day_end;
    var segments = Timeline.segments_for (
        plan, now, out day_start, out day_end);
    assert_dt (day_start, new GLib.DateTime.utc (2026, 10, 27, 0, 0, 0),
        "midnight day start");
    assert_dt (day_end, new GLib.DateTime.utc (2026, 10, 28, 0, 0, 0),
        "midnight day end");
    if (segments.length != 4) {
        GLib.error ("got %d segments, want 4", segments.length);
    }
    assert_dt (segments[0].start, day_start, "first segment start");
    assert_dt (segments[segments.length - 1].end, day_end,
        "last segment end");
    for (int i = 1; i < segments.length; i++) {
        if (segments[i].start.compare (segments[i - 1].end) < 0) {
            GLib.error ("segment %d overlaps previous", i);
        }
    }
}

void test_segments_invalid_plan () {
    var plan = new Plan ();
    plan.meals = 0;
    GLib.DateTime day_start;
    GLib.DateTime day_end;
    var segments = Timeline.segments_for (
        plan, new GLib.DateTime.utc (2026, 10, 26, 10, 0, 0),
        out day_start, out day_end);
    if (segments.length != 0) {
        GLib.error ("invalid plan produced %d segments", segments.length);
    }
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/timeline/segments", test_segments);
    GLib.Test.add_func ("/timeline/midnight", test_segments_crossing_midnight);
    GLib.Test.add_func ("/timeline/invalid", test_segments_invalid_plan);
    return GLib.Test.run ();
}
