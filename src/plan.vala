namespace Comtudia {

public errordomain PlanError {
    INVALID
}

public class Preset : GLib.Object {
    public string name { get; private set; }
    public int eating_minutes { get; private set; }
    public int meals { get; private set; }
    public int gap_minutes { get; private set; }

    public Preset (string name, int eating_minutes, int meals, int gap_minutes) {
        this.name = name;
        this.eating_minutes = eating_minutes;
        this.meals = meals;
        this.gap_minutes = gap_minutes;
    }
}

public class MealRange : GLib.Object {
    public int index { get; private set; }
    public int start_minutes { get; private set; }
    public int end_minutes { get; private set; }
    public int duration_minutes { get; private set; }

    public MealRange (int index, int start_minutes, int end_minutes, int duration_minutes) {
        this.index = index;
        this.start_minutes = start_minutes;
        this.end_minutes = end_minutes;
        this.duration_minutes = duration_minutes;
    }
}

/**
 * A recurring daily eating schedule. The cycle is always 24 hours.
 */
public class Plan : GLib.Object {
    public const int VERSION = 1;
    public const int MINUTES_PER_DAY = 24 * 60;
    public const int MAX_MEALS = 8;
    public const int MIN_MEAL_MINUTE = 15;
    public const int MIN_EATING = 60;
    public const int MIN_FASTING = 60;

    public const int DEFAULT_EATING_MINUTES = 6 * 60;
    public const int DEFAULT_MEALS = 3;
    public const int DEFAULT_GAP_MINUTES = 0;
    public const int DEFAULT_WINDOW_START = 12 * 60;
    public const bool DEFAULT_NOTIFICATIONS = true;
    public const bool DEFAULT_RUN_IN_TRAY = false;

    public int version { get; set; default = VERSION; }
    public int eating_minutes { get; set; }
    public int meals { get; set; }
    public int gap_minutes { get; set; }
    public int window_start_minutes { get; set; }
    public bool notifications { get; set; }
    public bool run_in_tray { get; set; }

    public Plan () {
        version = VERSION;
        eating_minutes = DEFAULT_EATING_MINUTES;
        meals = DEFAULT_MEALS;
        gap_minutes = DEFAULT_GAP_MINUTES;
        window_start_minutes = DEFAULT_WINDOW_START;
        notifications = DEFAULT_NOTIFICATIONS;
        run_in_tray = DEFAULT_RUN_IN_TRAY;
    }

    public static Plan create_default () {
        return new Plan ();
    }

    public static Preset[] presets () {
        return new Preset[] {
            new Preset ("16:8", 8 * 60, 2, 0),
            new Preset ("18:6", 6 * 60, 3, 0),
            new Preset ("20:4", 4 * 60, 2, 0)
        };
    }

    /**
     * Returns a human readable validation message, or null when valid.
     */
    public string? validate_message () {
        if (meals < 1 || meals > MAX_MEALS) {
            return "meal count must be between 1 and %d".printf (MAX_MEALS);
        }
        if (eating_minutes < MIN_EATING ||
            eating_minutes > MINUTES_PER_DAY - MIN_FASTING) {
            return "eating time must be between 1h and 23h";
        }
        if (window_start_minutes < 0 || window_start_minutes >= MINUTES_PER_DAY) {
            return "start time must be within the day";
        }
        if (gap_minutes < 0) {
            return "gap cannot be negative";
        }
        if (meals == 1 && gap_minutes != 0) {
            return "a single meal cannot have a gap";
        }
        if (eating_minutes / meals < MIN_MEAL_MINUTE) {
            return "each meal must be at least %d minutes".printf (MIN_MEAL_MINUTE);
        }
        if (span_minutes () > MINUTES_PER_DAY - MIN_FASTING) {
            return "the meal spread must be 23 hours or less";
        }
        return null;
    }

    public void validate () throws PlanError {
        var message = validate_message ();
        if (message != null) {
            throw new PlanError.INVALID (message);
        }
    }

    public bool is_valid () {
        return validate_message () == null;
    }

    public int fasting_minutes () {
        return MINUTES_PER_DAY - eating_minutes;
    }

    public int span_minutes () {
        if (meals <= 0) {
            return 0;
        }
        return eating_minutes + (meals - 1) * gap_minutes;
    }

    public int long_fast_minutes () {
        int n = MINUTES_PER_DAY - span_minutes ();
        return n < 0 ? 0 : n;
    }

    /**
     * Splits eating time into equal slots. Leftover minutes go to the earliest
     * slots.
     */
    public MealRange[] meal_ranges () {
        if (meals <= 0 || eating_minutes < meals) {
            return new MealRange[0];
        }

        int per_meal = eating_minutes / meals;
        int remainder = eating_minutes % meals;
        var result = new MealRange[0];
        int offset = 0;
        for (int i = 0; i < meals; i++) {
            int duration = per_meal;
            if (i < remainder) {
                duration++;
            }
            result += new MealRange (i + 1, offset, offset + duration, duration);
            offset += duration + gap_minutes;
        }
        return result;
    }

    public string summary () {
        return "%s fast · %s eat".printf (
            minutes_label (fasting_minutes ()),
            minutes_label (eating_minutes));
    }

    public bool equals_plan (Plan other) {
        return version == other.version &&
            eating_minutes == other.eating_minutes &&
            meals == other.meals &&
            gap_minutes == other.gap_minutes &&
            window_start_minutes == other.window_start_minutes &&
            notifications == other.notifications &&
            run_in_tray == other.run_in_tray;
    }

    public Plan copy () {
        var p = new Plan ();
        p.version = version;
        p.eating_minutes = eating_minutes;
        p.meals = meals;
        p.gap_minutes = gap_minutes;
        p.window_start_minutes = window_start_minutes;
        p.notifications = notifications;
        p.run_in_tray = run_in_tray;
        return p;
    }

    private static string minutes_label (int minutes) {
        if (minutes <= 0) {
            return "0h";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        if (m == 0) {
            return "%dh".printf (h);
        }
        if (h == 0) {
            return "%dm".printf (m);
        }
        return "%dh %02dm".printf (h, m);
    }
}

}
