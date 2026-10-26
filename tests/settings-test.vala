using Comtudia;

void test_preview_text () {
    var plan = Plan.create_default ();
    plan.gap_minutes = 60;
    var text = SettingsPage.preview_text (plan);
    string[] wanted = {
        "Meals",
        "12:00–14:00",
        "15:00–17:00",
        "18:00–20:00",
        "Eating 6h",
        "Long fast 16h",
        "meal gaps 2h"
    };
    foreach (var want in wanted) {
        if (!text.contains (want)) {
            GLib.error ("preview missing %s in:\n%s", want, text);
        }
    }
}

void test_presets () {
    var presets = Plan.presets ();
    if (presets.length != 3) {
        GLib.error ("got %d presets, want 3", presets.length);
    }
    GLib.assert_cmpstr (presets[0].name, GLib.CompareOperator.EQ, "16:8");
    GLib.assert_cmpint (presets[0].eating_minutes, GLib.CompareOperator.EQ, 480);
    GLib.assert_cmpint (presets[0].meals, GLib.CompareOperator.EQ, 2);
    GLib.assert_cmpstr (presets[1].name, GLib.CompareOperator.EQ, "18:6");
    GLib.assert_cmpint (presets[1].eating_minutes, GLib.CompareOperator.EQ, 360);
    GLib.assert_cmpstr (presets[2].name, GLib.CompareOperator.EQ, "20:4");
    GLib.assert_cmpint (presets[2].eating_minutes, GLib.CompareOperator.EQ, 240);
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/settings/preview", test_preview_text);
    GLib.Test.add_func ("/settings/presets", test_presets);
    return GLib.Test.run ();
}
