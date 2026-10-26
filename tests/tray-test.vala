using Comtudia;

State state_at (Plan plan, GLib.DateTime now) {
    try {
        return Schedule.compute (plan, now);
    } catch (PlanError e) {
        GLib.error ("Compute: %s", e.message);
    }
    return new State (now);
}

void test_status_text () {
    var plan = Plan.create_default ();
    var fasting = state_at (plan,
        new GLib.DateTime.utc (2026, 10, 26, 10, 0, 0));
    GLib.assert_cmpstr (TrayItem.status_text (fasting),
        GLib.CompareOperator.EQ, "Next eating in 2h");

    var eating = state_at (plan,
        new GLib.DateTime.utc (2026, 10, 26, 12, 30, 0));
    GLib.assert_cmpstr (TrayItem.status_text (eating),
        GLib.CompareOperator.EQ, "Eating until 18:00");
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/tray/status", test_status_text);
    return GLib.Test.run ();
}
