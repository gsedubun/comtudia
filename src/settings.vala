namespace Comtudia {

public delegate string? SavePlanFunc (Plan plan);
public delegate void VoidFunc ();

public class SettingsPage : GLib.Object {
    public Adw.NavigationPage page { get; private set; }

    private SavePlanFunc on_save;
    private VoidFunc on_cancel;
    private VoidFunc on_test;

    private Gtk.DropDown preset_dropdown;
    private Gtk.SpinButton eat_hours;
    private Gtk.SpinButton eat_minutes;
    private Gtk.SpinButton meals_spin;
    private Gtk.SpinButton gap_spin;
    private Gtk.SpinButton start_hours;
    private Gtk.SpinButton start_minutes;
    private Adw.SwitchRow notifications_row;
    private Adw.SwitchRow tray_row;
    private Gtk.Label eat_total;
    private Adw.ActionRow preview_row;
    private Adw.ActionRow error_row;
    private bool updating = false;
    private string? last_error = null;

    public SettingsPage (
            Plan plan, string config_path, bool tray_available,
            SavePlanFunc on_save, VoidFunc on_cancel, VoidFunc on_test) {
        this.on_save = on_save;
        this.on_cancel = on_cancel;
        this.on_test = on_test;

        var header = new Adw.HeaderBar ();
        var back = new Gtk.Button.from_icon_name ("go-previous-symbolic");
        back.set_tooltip_text ("Back");
        back.clicked.connect (() => this.on_cancel ());
        header.pack_start (back);

        var title = new Gtk.Label ("Plan");
        title.add_css_class ("title-2");
        header.set_title_widget (title);

        var save_button = new Gtk.Button.with_label ("Save");
        save_button.add_css_class ("suggested-action");
        save_button.clicked.connect (save);
        header.pack_end (save_button);

        var toolbar = new Adw.ToolbarView ();
        toolbar.add_top_bar (header);

        var prefs = new Adw.PreferencesPage ();

        var preset_group = new Adw.PreferencesGroup ();
        preset_group.set_title ("Presets");
        var preset_row = new Adw.ActionRow ();
        preset_row.set_title ("Preset");
        preset_row.set_subtitle ("Start from a common split");
        preset_row.set_activatable (false);
        preset_dropdown = new Gtk.DropDown.from_strings (
            new string[] { "16:8", "18:6", "20:4", "Custom" });
        preset_dropdown.set_valign (Gtk.Align.CENTER);
        preset_row.add_suffix (preset_dropdown);
        preset_group.add (preset_row);
        prefs.add (preset_group);

        var schedule_group = new Adw.PreferencesGroup ();
        schedule_group.set_title ("Schedule");

        eat_hours = make_spin (6, 1, 23);
        eat_minutes = make_spin (0, 0, 59);
        eat_total = new Gtk.Label ("");
        eat_total.add_css_class ("caption");
        eat_total.add_css_class ("dim-label");
        eat_total.set_xalign (1);

        var eating_controls = new Gtk.Box (Gtk.Orientation.HORIZONTAL, 4);
        eating_controls.set_valign (Gtk.Align.CENTER);
        eating_controls.append (eat_hours);
        eating_controls.append (new Gtk.Label ("h"));
        eating_controls.append (eat_minutes);
        eating_controls.append (new Gtk.Label ("min"));

        var eating_box = new Gtk.Box (Gtk.Orientation.VERTICAL, 2);
        eating_box.set_valign (Gtk.Align.CENTER);
        eating_box.append (eating_controls);
        eating_box.append (eat_total);

        var eating_row = new Adw.ActionRow ();
        eating_row.set_title ("Eating time");
        eating_row.set_subtitle ("Total across all meals");
        eating_row.set_activatable (false);
        eating_row.add_suffix (eating_box);
        schedule_group.add (eating_row);

        meals_spin = make_spin (3, 1, Plan.MAX_MEALS);
        var meals_row = new Adw.ActionRow ();
        meals_row.set_title ("Meals");
        meals_row.set_subtitle ("Split evenly across the eating time");
        meals_row.set_activatable (false);
        meals_row.add_suffix (meals_spin);
        schedule_group.add (meals_row);

        gap_spin = make_spin (0, 0, Plan.MINUTES_PER_DAY);
        var gap_row = new Adw.ActionRow ();
        gap_row.set_title ("Gap between meals");
        gap_row.set_subtitle ("Minutes; 0 = back-to-back");
        gap_row.set_activatable (false);
        gap_row.add_suffix (gap_spin);
        schedule_group.add (gap_row);

        start_hours = make_spin (12, 0, 23);
        start_minutes = make_spin (0, 0, 59);
        var start_controls = new Gtk.Box (Gtk.Orientation.HORIZONTAL, 4);
        start_controls.set_valign (Gtk.Align.CENTER);
        start_controls.append (start_hours);
        start_controls.append (new Gtk.Label (":"));
        start_controls.append (start_minutes);

        var start_row = new Adw.ActionRow ();
        start_row.set_title ("First meal");
        start_row.set_subtitle ("Local wall-clock start time");
        start_row.set_activatable (false);
        start_row.add_suffix (start_controls);
        schedule_group.add (start_row);
        prefs.add (schedule_group);

        var behavior_group = new Adw.PreferencesGroup ();
        behavior_group.set_title ("Behavior");
        notifications_row = new Adw.SwitchRow ();
        notifications_row.set_title ("Desktop notifications");
        notifications_row.set_subtitle (
            "Notify when eating or fasting starts");
        behavior_group.add (notifications_row);

        tray_row = new Adw.SwitchRow ();
        tray_row.set_title ("Keep running in tray when closed");
        tray_row.set_subtitle (tray_available
            ? "Closing the window hides it instead of quitting"
            : "No StatusNotifier watcher found; closing quits");
        tray_row.set_sensitive (tray_available);
        behavior_group.add (tray_row);
        prefs.add (behavior_group);

        var preview_group = new Adw.PreferencesGroup ();
        preview_group.set_title ("Preview");
        preview_row = new Adw.ActionRow ();
        preview_row.set_title ("Meal times");
        preview_row.set_subtitle ("");
        preview_row.set_subtitle_lines (12);
        preview_row.set_subtitle_selectable (true);
        preview_row.set_activatable (false);
        preview_row.add_css_class ("monospace");
        preview_group.add (preview_row);

        error_row = new Adw.ActionRow ();
        error_row.set_title ("Cannot save this plan");
        error_row.set_subtitle ("");
        error_row.set_subtitle_lines (3);
        error_row.set_subtitle_selectable (true);
        error_row.set_activatable (false);
        error_row.add_css_class ("error-row");
        error_row.set_visible (false);
        preview_group.add (error_row);
        prefs.add (preview_group);

        var actions_group = new Adw.PreferencesGroup ();
        actions_group.set_title ("Actions");

        var reset_row = new Adw.ActionRow ();
        reset_row.set_title ("Reset to defaults");
        reset_row.set_subtitle ("Restore the 18:6 plan");
        reset_row.set_activatable (false);
        var reset_button = new Gtk.Button.with_label ("Reset");
        reset_button.set_valign (Gtk.Align.CENTER);
        reset_button.clicked.connect (() => this.set_plan (Plan.create_default ()));
        reset_row.add_suffix (reset_button);
        actions_group.add (reset_row);

        var test_row = new Adw.ActionRow ();
        test_row.set_title ("Test notification");
        test_row.set_subtitle ("Send a test desktop notification");
        test_row.set_activatable (false);
        var test_button = new Gtk.Button.with_label ("Send test");
        test_button.set_valign (Gtk.Align.CENTER);
        test_button.clicked.connect (() => this.on_test ());
        test_row.add_suffix (test_button);
        actions_group.add (test_row);

        var config_row = new Adw.ActionRow ();
        config_row.set_title ("Config file");
        config_row.set_subtitle (config_path);
        config_row.set_subtitle_selectable (true);
        config_row.set_subtitle_lines (2);
        config_row.set_activatable (false);
        actions_group.add (config_row);
        prefs.add (actions_group);

        toolbar.set_content (prefs);
        page = new Adw.NavigationPage (toolbar, "Plan");

        preset_dropdown.notify["selected"].connect (on_preset_changed);
        foreach (var spin in new Gtk.SpinButton[] {
                eat_hours, eat_minutes, meals_spin, gap_spin,
                start_hours, start_minutes }) {
            spin.value_changed.connect (changed);
        }
        notifications_row.notify["active"].connect (changed);
        tray_row.notify["active"].connect (changed);

        set_plan (plan);
    }

    public void set_plan (Plan plan) {
        updating = true;
        eat_hours.set_value (plan.eating_minutes / 60);
        eat_minutes.set_value (plan.eating_minutes % 60);
        meals_spin.set_value (plan.meals);
        gap_spin.set_value (plan.gap_minutes);
        start_hours.set_value (plan.window_start_minutes / 60);
        start_minutes.set_value (plan.window_start_minutes % 60);
        notifications_row.set_active (plan.notifications);
        tray_row.set_active (plan.run_in_tray);
        updating = false;

        detect_preset ();
        update_preview ();
        hide_error ();
    }

    private static Gtk.SpinButton make_spin (
            double value, double lower, double upper) {
        var adjustment = new Gtk.Adjustment (
            value, lower, upper, 1, 10, 0);
        var spin = new Gtk.SpinButton (adjustment, 1, 0);
        spin.set_numeric (true);
        spin.set_size_request (64, -1);
        return spin;
    }

    private Plan collect_plan () {
        var plan = new Plan ();
        plan.eating_minutes =
            (int) eat_hours.get_value () * 60 +
            (int) eat_minutes.get_value ();
        plan.meals = (int) meals_spin.get_value ();
        plan.gap_minutes = (int) gap_spin.get_value ();
        plan.window_start_minutes =
            (int) start_hours.get_value () * 60 +
            (int) start_minutes.get_value ();
        plan.notifications = notifications_row.get_active ();
        plan.run_in_tray = tray_row.get_active ();
        return plan;
    }

    private Plan? current_plan () {
        var plan = collect_plan ();
        last_error = plan.validate_message ();
        return last_error == null ? plan : null;
    }

    private void changed () {
        if (updating) {
            return;
        }
        detect_preset ();
        update_preview ();
    }

    private void on_preset_changed () {
        if (updating) {
            return;
        }
        uint selected = preset_dropdown.get_selected ();
        var presets = Plan.presets ();
        if (selected >= presets.length) {
            return;
        }
        var preset = presets[selected];
        updating = true;
        eat_hours.set_value (preset.eating_minutes / 60);
        eat_minutes.set_value (preset.eating_minutes % 60);
        meals_spin.set_value (preset.meals);
        gap_spin.set_value (preset.gap_minutes);
        updating = false;
        update_preview ();
    }

    private void detect_preset () {
        var plan = collect_plan ();
        uint selected = 3;
        var presets = Plan.presets ();
        for (uint i = 0; i < presets.length; i++) {
            var preset = presets[i];
            if (plan.eating_minutes == preset.eating_minutes &&
                plan.meals == preset.meals &&
                plan.gap_minutes == preset.gap_minutes) {
                selected = i;
                break;
            }
        }
        updating = true;
        preset_dropdown.set_selected (selected);
        updating = false;
    }

    private void update_preview () {
        update_eating_total ();
        var plan = current_plan ();
        if (plan == null) {
            preview_row.set_subtitle (
                "Preview unavailable\n" + (last_error ?? "Invalid plan"));
            return;
        }
        preview_row.set_subtitle (preview_text (plan));
    }

    private void update_eating_total () {
        int hours = (int) eat_hours.get_value ();
        int minutes = (int) eat_minutes.get_value ();
        eat_total.set_text (
            "= " + Format.minutes (hours * 60 + minutes) + " total");
    }

    private void save () {
        var plan = current_plan ();
        if (plan == null) {
            show_error (last_error ?? "Invalid plan");
            return;
        }
        var error = on_save (plan);
        if (error != null) {
            show_error (error);
            return;
        }
        hide_error ();
    }

    private void show_error (string? message) {
        error_row.set_subtitle (message ?? "Invalid plan");
        error_row.set_visible (true);
    }

    private void hide_error () {
        error_row.set_visible (false);
    }

    public static string preview_text (Plan plan) {
        var text = new GLib.StringBuilder ("Meals\n");
        foreach (var range in plan.meal_ranges ()) {
            text.append_printf ("%d. %-20s %s\n",
                range.index,
                Format.range_minutes (
                    plan.window_start_minutes + range.start_minutes,
                    plan.window_start_minutes + range.end_minutes),
                Format.duration_compact (
                    (GLib.TimeSpan) range.duration_minutes *
                    GLib.TimeSpan.MINUTE));
        }
        text.append ("\n");
        text.append_printf ("Eating %s · Fasting %s\n",
            Format.minutes (plan.eating_minutes),
            Format.minutes (plan.fasting_minutes ()));
        if (plan.gap_minutes > 0) {
            text.append_printf ("Long fast %s · meal gaps %s\n",
                Format.minutes (plan.long_fast_minutes ()),
                Format.minutes ((plan.meals - 1) * plan.gap_minutes));
        } else {
            text.append_printf ("Long fast %s\n",
                Format.minutes (plan.long_fast_minutes ()));
        }
        return text.str;
    }
}

}
