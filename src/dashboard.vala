namespace Comtudia {

private class StatCard : GLib.Object {
    public Gtk.Box widget { get; private set; }
    public Gtk.Label title_label { get; private set; }
    public Gtk.Label value_label { get; private set; }
    public Gtk.Label sub_label { get; private set; }

    public StatCard (string title) {
        widget = new Gtk.Box (Gtk.Orientation.VERTICAL, 3);
        widget.add_css_class ("stat-card");
        widget.set_hexpand (true);

        title_label = new Gtk.Label (title);
        title_label.add_css_class ("caption");
        title_label.add_css_class ("dim-label");
        title_label.set_xalign (0.0f);

        value_label = new Gtk.Label ("");
        value_label.add_css_class ("title-2");
        value_label.set_xalign (0.0f);

        sub_label = new Gtk.Label ("");
        sub_label.add_css_class ("caption");
        sub_label.add_css_class ("dim-label");
        sub_label.set_xalign (0.0f);
        sub_label.set_wrap (true);

        widget.append (title_label);
        widget.append (value_label);
        widget.append (sub_label);
    }

    public void set (string title, string value, string sub) {
        title_label.set_text (title);
        value_label.set_text (value);
        sub_label.set_text (sub);
    }
}

private class MealRow : GLib.Object {
    public Gtk.ListBoxRow row { get; private set; }
    public Gtk.Box box { get; private set; }
    public Gtk.Label status { get; private set; }
    public int index { get; private set; }

    public MealRow (Slot slot) {
        index = slot.index;
        box = new Gtk.Box (Gtk.Orientation.HORIZONTAL, 10);
        box.add_css_class ("meal-row");

        var index_label = new Gtk.Label ("%d".printf (slot.index));
        index_label.add_css_class ("meal-index");
        index_label.set_size_request (22, -1);
        index_label.set_xalign (0.5f);

        var times = new Gtk.Label (Format.time_range (slot.start, slot.end));
        times.set_xalign (0.0f);
        times.set_hexpand (true);

        var duration = new Gtk.Label (
            Format.duration_compact (slot.duration ()));
        duration.add_css_class ("dim-label");

        status = new Gtk.Label ("");
        status.add_css_class ("meal-status");
        status.set_size_request (48, -1);
        status.set_xalign (1.0f);

        box.append (index_label);
        box.append (times);
        box.append (duration);
        box.append (status);

        row = new Gtk.ListBoxRow ();
        row.set_child (box);
        row.set_activatable (false);
        row.set_selectable (false);
        row.add_css_class ("meal-row-wrapper");
    }
}

public class Dashboard : Gtk.Box {
    public signal void plan_requested ();

    private Plan plan;
    private Gtk.Label summary_label;
    private Gtk.Label notice_label;
    private Gtk.Label pill;
    private Ring ring;
    private Gtk.Label countdown_label;
    private Gtk.Label context_label;
    private StatCard next_card;
    private StatCard plan_card;
    private Timeline timeline;
    private Gtk.Label schedule_heading;
    private Gtk.ListBox schedule_list;
    private MealRow[] rows = new MealRow[0];
    private GLib.DateTime list_day;

    public Dashboard (Plan plan, string? load_error) {
        Object (orientation: Gtk.Orientation.VERTICAL, spacing: 0);
        this.plan = plan;

        var header = new Adw.HeaderBar ();
        var title = new Gtk.Label ("comtudia");
        title.add_css_class ("title-2");
        header.set_title_widget (title);

        var plan_button = new Gtk.Button.with_label ("Plan");
        plan_button.clicked.connect (() => plan_requested ());
        header.pack_end (plan_button);

        var toolbar = new Adw.ToolbarView ();
        toolbar.add_top_bar (header);

        var content = new Gtk.Box (Gtk.Orientation.VERTICAL, 14);
        content.set_margin_top (18);
        content.set_margin_bottom (24);
        content.set_margin_start (18);
        content.set_margin_end (18);

        summary_label = new Gtk.Label (plan.summary ());
        summary_label.add_css_class ("dim-label");
        summary_label.set_xalign (0.0f);
        content.append (summary_label);

        notice_label = new Gtk.Label ("");
        notice_label.add_css_class ("notice");
        notice_label.set_xalign (0.0f);
        notice_label.set_wrap (true);
        notice_label.set_visible (false);
        if (load_error != null) {
            notice_label.set_text (
                "Could not load the saved plan — using defaults. " + load_error);
            notice_label.set_visible (true);
        }
        content.append (notice_label);

        pill = new Gtk.Label ("FASTING");
        pill.add_css_class ("status-pill");
        pill.add_css_class ("fasting");
        pill.set_halign (Gtk.Align.CENTER);
        content.append (pill);

        ring = new Ring ();
        countdown_label = new Gtk.Label ("00:00:00");
        countdown_label.add_css_class ("ring-countdown");
        context_label = new Gtk.Label ("");
        context_label.add_css_class ("ring-subtitle");
        context_label.set_wrap (true);

        var ring_labels = new Gtk.Box (Gtk.Orientation.VERTICAL, 2);
        ring_labels.set_halign (Gtk.Align.CENTER);
        ring_labels.set_valign (Gtk.Align.CENTER);
        ring_labels.append (countdown_label);
        ring_labels.append (context_label);

        var overlay = new Gtk.Overlay ();
        overlay.set_child (ring);
        overlay.add_overlay (ring_labels);
        overlay.set_size_request (280, 280);
        overlay.set_halign (Gtk.Align.CENTER);
        content.append (overlay);

        next_card = new StatCard ("Next eating");
        plan_card = new StatCard ("Your plan");
        var cards = new Gtk.Grid ();
        cards.set_column_spacing (12);
        cards.set_row_spacing (12);
        cards.set_column_homogeneous (true);
        cards.attach (next_card.widget, 0, 0, 1, 1);
        cards.attach (plan_card.widget, 1, 0, 1, 1);
        content.append (cards);

        var timeline_heading = new Gtk.Label ("24-hour timeline");
        timeline_heading.add_css_class ("heading");
        timeline_heading.set_xalign (0.0f);
        content.append (timeline_heading);

        timeline = new Timeline (plan, new GLib.DateTime.now_local ());
        content.append (timeline);

        schedule_heading = new Gtk.Label ("Schedule");
        schedule_heading.add_css_class ("heading");
        schedule_heading.set_xalign (0.0f);
        content.append (schedule_heading);

        schedule_list = new Gtk.ListBox ();
        schedule_list.add_css_class ("meal-list");
        schedule_list.set_selection_mode (Gtk.SelectionMode.NONE);
        schedule_list.set_show_separators (false);
        content.append (schedule_list);

        var scrolled = new Gtk.ScrolledWindow ();
        scrolled.set_policy (Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC);
        scrolled.set_vexpand (true);
        scrolled.set_child (content);
        toolbar.set_content (scrolled);

        append (toolbar);
        set_plan (plan);
    }

    public void set_plan (Plan plan) {
        this.plan = plan;
        summary_label.set_text (plan.summary ());
        plan_card.set ("Your plan", plan.summary (), plan_detail (plan));
        timeline.set_plan (plan);
        set_schedule_data (plan, new GLib.DateTime.now_local ());
    }

    public void update (State state) {
        bool eating = state.phase == Phase.EATING;
        pill.remove_css_class ("eating");
        pill.remove_css_class ("fasting");
        pill.add_css_class (eating ? "eating" : "fasting");
        pill.set_text (eating ? "EATING NOW" : "FASTING");

        ring.set_state (state.progress, eating);
        countdown_label.set_text (Format.countdown (state.remaining));
        context_label.set_text (context_text (state));

        if (eating) {
            bool single = state.first_meal_index == state.last_meal_index;
            string title = single ? "Meal ends" : "Window ends";
            string sub = "next eating soon";
            if (state.next_eating_phase_start != null) {
                sub = "next eating " + Format.day_clock (
                    state.next_eating_phase_start, state.now);
            }
            next_card.set (title, Format.duration (state.remaining), sub);
        } else {
            string sub = "opens soon";
            if (state.next_eating_phase_start != null) {
                sub = "opens " + Format.day_clock (
                    state.next_eating_phase_start, state.now);
            }
            next_card.set ("Next eating", Format.duration (state.remaining), sub);
        }

        timeline.set_now (state.now);
        update_schedule (state);
    }

    private string context_text (State state) {
        if (state.phase == Phase.EATING) {
            if (state.first_meal_index == state.last_meal_index) {
                return "meal %d of %d · ends %s".printf (
                    state.first_meal_index,
                    state.meal_count,
                    Format.clock (state.phase_end));
            }
            return "meals %d–%d · window ends %s".printf (
                state.first_meal_index,
                state.last_meal_index,
                Format.clock (state.phase_end));
        }
        if (state.next_eating_phase_start != null) {
            return "next eating " + Format.when (
                state.next_eating_phase_start, state.now);
        }
        return "next eating soon";
    }

    private void set_schedule_data (Plan plan, GLib.DateTime day) {
        list_day = normalize_day (day);
        while (schedule_list.get_first_child () != null) {
            schedule_list.remove (schedule_list.get_first_child ());
        }

        rows = new MealRow[0];
        var slots = Schedule.slots_for_date (plan, list_day);
        if (slots.length == 0) {
            var empty = new Gtk.Label ("No meals configured.");
            empty.set_xalign (0.0f);
            empty.add_css_class ("dim-label");
            empty.set_margin_top (6);
            empty.set_margin_bottom (6);
            var empty_row = new Gtk.ListBoxRow ();
            empty_row.set_child (empty);
            empty_row.set_activatable (false);
            empty_row.set_selectable (false);
            schedule_list.append (empty_row);
        } else {
            foreach (var slot in slots) {
                var row = new MealRow (slot);
                rows += row;
                schedule_list.append (row.row);
            }
        }
    }

    private void update_schedule (State state) {
        var day = state_day (state);
        if (list_day == null || !Format.same_date (day, list_day)) {
            set_schedule_data (plan, day);
        }
        if (rows.length == 0) {
            return;
        }

        int current = 0;
        if (state.phase == Phase.EATING &&
            state.current_meal != null &&
            Format.same_date (state.current_meal.day, list_day)) {
            current = state.current_meal.index;
        }

        int next = 0;
        if (state.next_eating_phase_meal > 0 &&
            state.next_eating_phase_start != null &&
            Format.same_date (state.next_eating_phase_start, list_day)) {
            if (state.phase != Phase.EATING ||
                state.next_eating_phase_meal != current) {
                next = state.next_eating_phase_meal;
            }
        }

        foreach (var row in rows) {
            row.box.remove_css_class ("active");
            row.box.remove_css_class ("next");
            if (row.index == current) {
                row.box.add_css_class ("active");
                row.status.set_text ("NOW");
            } else if (row.index == next) {
                row.box.add_css_class ("next");
                row.status.set_text ("NEXT");
            } else {
                row.status.set_text ("");
            }
        }

        schedule_heading.set_text (
            "Schedule · " + Format.day_name (list_day, state.now));
    }

    private static GLib.DateTime state_day (State state) {
        if (state.current_meal != null) {
            return state.current_meal.day;
        }
        if (state.next_meal != null) {
            return state.next_meal.day;
        }
        return normalize_day (state.now);
    }

    private static GLib.DateTime normalize_day (GLib.DateTime time) {
        int year, month, day;
        time.get_ymd (out year, out month, out day);
        return new GLib.DateTime (
            time.get_timezone (), year, month, day, 0, 0, 0);
    }

    private static string plan_detail (Plan plan) {
        string meal_duration = "";
        var ranges = plan.meal_ranges ();
        if (ranges.length > 0) {
            meal_duration = Format.duration_compact (
                (GLib.TimeSpan) ranges[0].duration_minutes *
                GLib.TimeSpan.MINUTE);
        }
        string detail = "%d × %s · starts %s".printf (
            plan.meals,
            meal_duration,
            Format.clock_minutes (plan.window_start_minutes));
        if (plan.gap_minutes > 0) {
            detail += " · " + Format.duration_compact (
                (GLib.TimeSpan) plan.gap_minutes * GLib.TimeSpan.MINUTE) +
                " gap";
        }
        return detail;
    }
}

}
