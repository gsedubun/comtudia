using Comtudia;

void assert_int (int actual, int expected, string label) {
    if (actual != expected) {
        GLib.error ("%s: got %d, want %d", label, actual, expected);
    }
}

void test_default () {
    var plan = Plan.create_default ();
    GLib.assert_true (plan.is_valid ());
    assert_int (plan.fasting_minutes (), 18 * 60, "fasting");
    assert_int (plan.span_minutes (), 6 * 60, "span");
    assert_int (plan.long_fast_minutes (), 18 * 60, "long fast");
}

Plan plan_with (int eating, int meals, int gap, int start) {
    var plan = new Plan ();
    plan.eating_minutes = eating;
    plan.meals = meals;
    plan.gap_minutes = gap;
    plan.window_start_minutes = start;
    return plan;
}

void test_validate () {
    GLib.assert_true (Plan.create_default ().is_valid ());
    GLib.assert_true (!plan_with (360, 0, 0, 720).is_valid ());
    GLib.assert_true (!plan_with (360, 9, 0, 720).is_valid ());
    GLib.assert_true (!plan_with (0, 1, 0, 720).is_valid ());
    GLib.assert_true (!plan_with (1440, 3, 0, 720).is_valid ());
    GLib.assert_true (!plan_with (360, 3, 0, -1).is_valid ());
    GLib.assert_true (!plan_with (360, 3, 0, 1440).is_valid ());
    GLib.assert_true (!plan_with (360, 3, -1, 720).is_valid ());
    GLib.assert_true (!plan_with (360, 1, 60, 720).is_valid ());
    GLib.assert_true (!plan_with (60, 5, 0, 720).is_valid ());
    GLib.assert_true (!plan_with (360, 3, 600, 720).is_valid ());
    GLib.assert_true (!plan_with (360, 3, 540, 0).is_valid ());
    GLib.assert_true (!plan_with (1380, 2, 1, 0).is_valid ());
}

void test_meal_ranges_contiguous () {
    var plan = Plan.create_default ();
    var ranges = plan.meal_ranges ();
    assert_int (ranges.length, 3, "range count");
    int[] starts = { 0, 120, 240 };
    int[] ends = { 120, 240, 360 };
    for (int i = 0; i < 3; i++) {
        assert_int (ranges[i].index, i + 1, "range index");
        assert_int (ranges[i].start_minutes, starts[i], "range start");
        assert_int (ranges[i].end_minutes, ends[i], "range end");
        assert_int (ranges[i].duration_minutes, 120, "range duration");
    }
}

void test_meal_ranges_with_gap () {
    var plan = Plan.create_default ();
    plan.gap_minutes = 60;
    var ranges = plan.meal_ranges ();
    assert_int (ranges.length, 3, "range count");
    int[] starts = { 0, 180, 360 };
    int[] ends = { 120, 300, 480 };
    for (int i = 0; i < 3; i++) {
        assert_int (ranges[i].start_minutes, starts[i], "range start");
        assert_int (ranges[i].end_minutes, ends[i], "range end");
    }
    assert_int (plan.span_minutes (), 480, "span");
    assert_int (plan.long_fast_minutes (), 16 * 60, "long fast");
}

void test_meal_ranges_remainder () {
    var plan = plan_with (100, 3, 0, 0);
    var ranges = plan.meal_ranges ();
    assert_int (ranges.length, 3, "range count");
    assert_int (ranges[0].duration_minutes, 34, "first duration");
    assert_int (ranges[1].duration_minutes, 33, "second duration");
    assert_int (ranges[2].duration_minutes, 33, "third duration");
    assert_int (ranges[2].end_minutes, 100, "last end");
}

void test_summary () {
    var plan = Plan.create_default ();
    GLib.assert_cmpstr (plan.summary (), GLib.CompareOperator.EQ,
        "18h fast · 6h eat");
    plan.gap_minutes = 60;
    plan.window_start_minutes = 8 * 60;
    GLib.assert_cmpstr (plan.summary (), GLib.CompareOperator.EQ,
        "18h fast · 6h eat");
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/plan/default", test_default);
    GLib.Test.add_func ("/plan/validate", test_validate);
    GLib.Test.add_func ("/plan/meal-ranges-contiguous", test_meal_ranges_contiguous);
    GLib.Test.add_func ("/plan/meal-ranges-gap", test_meal_ranges_with_gap);
    GLib.Test.add_func ("/plan/meal-ranges-remainder", test_meal_ranges_remainder);
    GLib.Test.add_func ("/plan/summary", test_summary);
    return GLib.Test.run ();
}
