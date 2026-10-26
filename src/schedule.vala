namespace Comtudia {

public enum Phase {
    FASTING,
    EATING
}

public class Slot : GLib.Object {
    public int index { get; private set; }
    public GLib.DateTime day { get; private set; }
    public GLib.DateTime start { get; private set; }
    public GLib.DateTime end { get; private set; }
    internal int sort_id { get; set; }

    public Slot (int index, GLib.DateTime day, GLib.DateTime start, GLib.DateTime end) {
        this.index = index;
        this.day = day;
        this.start = start;
        this.end = end;
    }

    public GLib.TimeSpan duration () {
        return end.difference (start);
    }
}

/**
 * The computed schedule at a single instant.
 */
public class State : GLib.Object {
    public GLib.DateTime now { get; set; }
    public Phase phase { get; set; default = Phase.FASTING; }
    public GLib.DateTime? phase_start { get; set; }
    public GLib.DateTime? phase_end { get; set; }
    public Slot? current_meal { get; set; }
    public Slot? next_meal { get; set; }
    public GLib.DateTime? next_eating_phase_start { get; set; }
    public int next_eating_phase_meal { get; set; }
    public GLib.DateTime? next_window_start { get; set; }
    public int first_meal_index { get; set; }
    public int last_meal_index { get; set; }
    public int meal_count { get; set; }
    public GLib.TimeSpan remaining { get; set; }
    public double progress { get; set; }
    public GLib.TimeSpan fasting_total { get; set; }
    public GLib.TimeSpan long_fast { get; set; }

    public State (GLib.DateTime now) {
        this.now = now;
    }

    /**
     * Identifies a contiguous phase. It changes exactly when notifications
     * should fire.
     */
    public string phase_key () {
        if (phase_start == null) {
            return "%d/".printf ((int) phase);
        }
        return "%d/%s.%06d".printf (
            (int) phase,
            phase_start.to_unix ().to_string (),
            phase_start.get_microsecond ());
    }
}

public class Schedule : GLib.Object {
    /**
     * Returns the meals belonging to the cycle that starts on day. The day is
     * normalized to local midnight. Durations are preserved across DST.
     */
    public static Slot[] slots_for_date (Plan plan, GLib.DateTime day) {
        var ranges = plan.meal_ranges ();
        if (ranges.length == 0) {
            return new Slot[0];
        }

        int year, month, day_of_month;
        day.get_ymd (out year, out month, out day_of_month);
        var tz = day.get_timezone ();
        var day_start = new GLib.DateTime (
            tz, year, month, day_of_month, 0, 0, 0);
        var window_start = new GLib.DateTime (
            tz, year, month, day_of_month,
            plan.window_start_minutes / 60,
            plan.window_start_minutes % 60,
            0);

        var slots = new Slot[0];
        foreach (var range in ranges) {
            slots += new Slot (
                range.index,
                day_start,
                window_start.add_minutes (range.start_minutes),
                window_start.add_minutes (range.end_minutes));
        }
        return slots;
    }

    /**
     * Calculates the current phase and the next events around now.
     */
    public static State compute (Plan plan, GLib.DateTime now) throws PlanError {
        plan.validate ();

        var tz = now.get_timezone ();
        int year, month, day_of_month;
        now.get_ymd (out year, out month, out day_of_month);
        var day = new GLib.DateTime (tz, year, month, day_of_month, 0, 0, 0);

        var slots = new Slot[0];
        foreach (var slot in slots_for_date (plan, day.add_days (-1))) {
            slots += slot;
        }
        foreach (var slot in slots_for_date (plan, day)) {
            slots += slot;
        }
        foreach (var slot in slots_for_date (plan, day.add_days (1))) {
            slots += slot;
        }

        for (int i = 0; i < slots.length; i++) {
            slots[i].sort_id = i;
        }
        // Stable insertion sort, matching the Go implementation.
        for (int i = 1; i < slots.length; i++) {
            var item = slots[i];
            int j = i - 1;
            while (j >= 0 && compare_slots (slots[j], item) > 0) {
                slots[j + 1] = slots[j];
                j--;
            }
            slots[j + 1] = item;
        }

        var run_start = new int[slots.length];
        var run_end = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            if (i > 0 && slots[i].start.equal (slots[i - 1].end)) {
                run_start[i] = run_start[i - 1];
            } else {
                run_start[i] = i;
            }
        }
        for (int i = slots.length - 1; i >= 0; i--) {
            if (i < slots.length - 1 && slots[i].end.equal (slots[i + 1].start)) {
                run_end[i] = run_end[i + 1];
            } else {
                run_end[i] = i;
            }
        }

        var state = new State (now);
        state.meal_count = plan.meals;
        state.fasting_total = (GLib.TimeSpan) plan.fasting_minutes () * GLib.TimeSpan.MINUTE;
        state.long_fast = (GLib.TimeSpan) plan.long_fast_minutes () * GLib.TimeSpan.MINUTE;

        int current = -1;
        for (int i = 0; i < slots.length; i++) {
            if (now.compare (slots[i].start) >= 0 && now.compare (slots[i].end) < 0) {
                current = i;
                break;
            }
        }

        if (current >= 0) {
            int first = run_start[current];
            int last = run_end[current];
            state.phase = Phase.EATING;
            state.current_meal = slots[current];
            state.phase_start = slots[first].start;
            state.phase_end = slots[last].end;
            state.first_meal_index = slots[first].index;
            state.last_meal_index = slots[last].index;
            if (current + 1 < slots.length) {
                state.next_meal = slots[current + 1];
            }
            if (last + 1 < slots.length) {
                state.next_eating_phase_start = slots[last + 1].start;
                state.next_eating_phase_meal = slots[last + 1].index;
            }
        } else {
            int next = -1;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i].start.compare (now) > 0) {
                    next = i;
                    break;
                }
            }
            if (next < 0) {
                throw new PlanError.INVALID ("no upcoming meal found");
            }

            state.phase = Phase.FASTING;
            state.next_meal = slots[next];
            state.phase_end = slots[next].start;
            state.first_meal_index = slots[next].index;
            state.last_meal_index = slots[run_end[next]].index;
            state.next_eating_phase_start = slots[next].start;
            state.next_eating_phase_meal = slots[next].index;

            GLib.DateTime? previous_end = null;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i].end.compare (now) <= 0) {
                    previous_end = slots[i].end;
                }
            }
            if (previous_end == null) {
                previous_end = state.phase_end;
            }
            state.phase_start = previous_end;
        }

        for (int i = 0; i < slots.length; i++) {
            if (slots[i].index != 1) {
                continue;
            }
            if (slots[i].start.compare (now) > 0) {
                state.next_window_start = slots[i].start;
                break;
            }
        }

        if (state.phase_end != null) {
            var remaining = state.phase_end.difference (now);
            if (remaining < 0) {
                remaining = 0;
            }
            state.remaining = remaining;

            if (state.phase_start != null) {
                var total = state.phase_end.difference (state.phase_start);
                if (total > 0) {
                    double progress =
                        (double) now.difference (state.phase_start) / (double) total;
                    if (progress < 0) {
                        progress = 0;
                    } else if (progress > 1) {
                        progress = 1;
                    }
                    state.progress = progress;
                }
            }
        }

        return state;
    }

    public static string phase_name (Phase phase) {
        return phase == Phase.EATING ? "eating" : "fasting";
    }

    private static int compare_slots (Slot a, Slot b) {
        int c = a.start.compare (b.start);
        if (c != 0) {
            return c;
        }
        return a.sort_id - b.sort_id;
    }
}

}
