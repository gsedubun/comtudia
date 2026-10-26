namespace Comtudia {

public class Format : GLib.Object {
    private const int64 USEC_PER_SECOND = 1000 * 1000;
    private const int64 USEC_PER_MINUTE = 60 * USEC_PER_SECOND;
    private const int MINUTES_PER_DAY = 24 * 60;

    /**
     * Formats a duration as HH:MM:SS, truncating partial seconds.
     */
    public static string countdown (GLib.TimeSpan duration) {
        if (duration <= 0) {
            return "00:00:00";
        }
        int64 total = duration / USEC_PER_SECOND;
        int64 h = total / 3600;
        int64 m = (total % 3600) / 60;
        int64 s = total % 60;
        return "%02lld:%02lld:%02lld".printf (h, m, s);
    }

    /**
     * Formats a duration for cards and tray labels, e.g. "17h 42m".
     */
    public static string duration (GLib.TimeSpan value) {
        if (value <= 0) {
            return "0m";
        }
        int64 minutes = (value + USEC_PER_MINUTE / 2) / USEC_PER_MINUTE;
        if (minutes < 1) {
            return "1m";
        }
        int64 h = minutes / 60;
        int64 m = minutes % 60;
        if (h == 0) {
            return "%lldm".printf (m);
        }
        if (m == 0) {
            return "%lldh".printf (h);
        }
        return "%lldh %02lldm".printf (h, m);
    }

    /**
     * Formats a precise minute-ish duration, e.g. "2h" or "1h 30m".
     */
    public static string duration_compact (GLib.TimeSpan value) {
        if (value <= 0) {
            return "0m";
        }
        int64 total = (value + USEC_PER_MINUTE / 2) / USEC_PER_MINUTE;
        int64 h = total / 60;
        int64 m = total % 60;
        if (h == 0) {
            return "%lldm".printf (m);
        }
        if (m == 0) {
            return "%lldh".printf (h);
        }
        return "%lldh %02lldm".printf (h, m);
    }

    public static string minutes (int value) {
        return duration ((GLib.TimeSpan) value * GLib.TimeSpan.MINUTE);
    }

    /**
     * Formats a time as 24-hour HH:MM.
     */
    public static string clock (GLib.DateTime time) {
        return "%02d:%02d".printf (time.get_hour (), time.get_minute ());
    }

    /**
     * Formats minutes after midnight, which may overflow into following days,
     * as HH:MM.
     */
    public static string clock_minutes (int value) {
        int normalized = ((value % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        return "%02d:%02d".printf (normalized / 60, normalized % 60);
    }

    /**
     * Formats two times as a range, noting a following day when needed.
     */
    public static string time_range (GLib.DateTime start, GLib.DateTime end) {
        string suffix = same_date (start, end) ? "" : " (+1)";
        return clock (start) + "–" + clock (end) + suffix;
    }

    /**
     * Formats minute offsets from the start of the eating window.
     */
    public static string range_minutes (int start, int end) {
        string suffix = end / MINUTES_PER_DAY > start / MINUTES_PER_DAY ? " (+1)" : "";
        return clock_minutes (start) + "–" + clock_minutes (end) + suffix;
    }

    /**
     * Formats a time relative to now, e.g. "Today 12:00".
     */
    public static string day_clock (GLib.DateTime time, GLib.DateTime now) {
        if (same_date (time, now)) {
            return "Today " + clock (time);
        }
        if (same_date (time, now.add_days (1))) {
            return "Tomorrow " + clock (time);
        }
        if (same_date (time, now.add_days (-1))) {
            return "Yesterday " + clock (time);
        }
        return weekday_short (time) + " " + clock (time);
    }

    /**
     * Formats a time for a sentence, e.g. "tomorrow at 12:00".
     */
    public static string when (GLib.DateTime time, GLib.DateTime now) {
        if (same_date (time, now)) {
            return "today at " + clock (time);
        }
        if (same_date (time, now.add_days (1))) {
            return "tomorrow at " + clock (time);
        }
        if (same_date (time, now.add_days (-1))) {
            return "yesterday at " + clock (time);
        }
        return weekday_short (time) + " at " + clock (time);
    }

    public static bool same_date (GLib.DateTime a, GLib.DateTime b) {
        return a.get_year () == b.get_year () &&
            a.get_month () == b.get_month () &&
            a.get_day_of_month () == b.get_day_of_month ();
    }

    /**
     * Names a date relative to now: Today, Tomorrow, Yesterday, or the weekday.
     */
    public static string day_name (GLib.DateTime time, GLib.DateTime now) {
        if (same_date (time, now)) {
            return "Today";
        }
        if (same_date (time, now.add_days (1))) {
            return "Tomorrow";
        }
        if (same_date (time, now.add_days (-1))) {
            return "Yesterday";
        }
        return weekday_long (time);
    }

    private static string weekday_short (GLib.DateTime time) {
        switch (time.get_day_of_week ()) {
        case 1: return "Mon";
        case 2: return "Tue";
        case 3: return "Wed";
        case 4: return "Thu";
        case 5: return "Fri";
        case 6: return "Sat";
        default: return "Sun";
        }
    }

    private static string weekday_long (GLib.DateTime time) {
        switch (time.get_day_of_week ()) {
        case 1: return "Monday";
        case 2: return "Tuesday";
        case 3: return "Wednesday";
        case 4: return "Thursday";
        case 5: return "Friday";
        case 6: return "Saturday";
        default: return "Sunday";
        }
    }
}

}
