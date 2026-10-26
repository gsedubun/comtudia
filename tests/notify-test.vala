using Comtudia;

State state_at (Plan plan, GLib.DateTime now) {
    try {
        return Schedule.compute (plan, now);
    } catch (PlanError e) {
        GLib.error ("Compute: %s", e.message);
    }
    return new State (now);
}

GLib.DateTime utc (int year, int month, int day, int hour, int minute) {
    return new GLib.DateTime.utc (year, month, day, hour, minute, 0);
}

void test_contiguous_meals () {
    var plan = Plan.create_default ();
    var day = utc (2026, 10, 26, 0, 0);
    var notifier = new Notifier ();

    GLib.assert_true (notifier.update (state_at (plan, day.add_hours (10))) == null);
    GLib.assert_true (notifier.update (state_at (plan, day.add_hours (11))) == null);

    var message = notifier.update (state_at (plan, day.add_hours (12)));
    GLib.assert_true (message != null);
    GLib.assert_cmpstr (message.title, GLib.CompareOperator.EQ,
        "Eating window open");
    GLib.assert_true (message.body.contains ("Meals 1–3 until 18:00"));

    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (14))) == null);
    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (17))) == null);

    message = notifier.update (state_at (plan, day.add_hours (18)));
    GLib.assert_true (message != null);
    GLib.assert_cmpstr (message.title, GLib.CompareOperator.EQ,
        "Fasting started");
    GLib.assert_true (message.body.contains ("tomorrow at 12:00"));

    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (23))) == null);
}

void test_with_gaps () {
    var plan = Plan.create_default ();
    plan.gap_minutes = 60;
    var day = utc (2026, 10, 26, 0, 0);
    var notifier = new Notifier ();

    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (14).add_minutes (30))) == null);

    var message = notifier.update (state_at (plan, day.add_hours (15)));
    GLib.assert_true (message != null);
    GLib.assert_cmpstr (message.title, GLib.CompareOperator.EQ, "Eating time");
    GLib.assert_true (message.body.contains ("Meal 2 of 3"));

    message = notifier.update (state_at (plan, day.add_hours (17)));
    GLib.assert_true (message != null);
    GLib.assert_cmpstr (message.title, GLib.CompareOperator.EQ,
        "Fasting started");
    GLib.assert_true (message.body.contains ("today at 18:00"));

    message = notifier.update (state_at (plan, day.add_hours (18)));
    GLib.assert_true (message != null);
    GLib.assert_cmpstr (message.title, GLib.CompareOperator.EQ, "Eating time");
    GLib.assert_true (message.body.contains ("Meal 3 of 3"));
}

void test_reset_primes_again () {
    var plan = Plan.create_default ();
    var day = utc (2026, 10, 26, 0, 0);
    var notifier = new Notifier ();

    notifier.update (state_at (plan, day.add_hours (10)));
    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (12))) != null);

    notifier.reset ();
    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (12))) == null);
    GLib.assert_true (notifier.update (
        state_at (plan, day.add_hours (18))) != null);
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/notify/contiguous", test_contiguous_meals);
    GLib.Test.add_func ("/notify/gaps", test_with_gaps);
    GLib.Test.add_func ("/notify/reset", test_reset_primes_again);
    return GLib.Test.run ();
}
