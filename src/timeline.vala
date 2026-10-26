namespace Comtudia {

public class TimelineSegment : GLib.Object {
    public GLib.DateTime start { get; private set; }
    public GLib.DateTime end { get; private set; }

    public TimelineSegment (GLib.DateTime start, GLib.DateTime end) {
        this.start = start;
        this.end = end;
    }
}

/**
 * A 24-hour timeline drawn with Cairo on a Gtk.DrawingArea.
 */
public class Timeline : Gtk.DrawingArea {
    private Plan plan;
    private GLib.DateTime now;
    private GLib.DateTime day_start;
    private GLib.DateTime day_end;
    private TimelineSegment[] segments = new TimelineSegment[0];

    public Timeline (Plan plan, GLib.DateTime now) {
        this.plan = plan;
        this.now = now;
        set_content_height (66);
        set_size_request (240, 66);
        set_hexpand (true);
        set_draw_func (draw);
        refresh_segments ();
    }

    public void set_plan (Plan plan) {
        this.plan = plan;
        refresh_segments ();
        queue_draw ();
    }

    public void set_now (GLib.DateTime now) {
        this.now = now;
        refresh_segments ();
        queue_draw ();
    }

    public static TimelineSegment[] segments_for (
            Plan plan, GLib.DateTime now,
            out GLib.DateTime day_start, out GLib.DateTime day_end) {
        int year, month, day;
        now.get_ymd (out year, out month, out day);
        day_start = new GLib.DateTime (
            now.get_timezone (), year, month, day, 0, 0, 0);
        day_end = day_start.add_days (1);

        var segments = new TimelineSegment[0];
        var days = new GLib.DateTime[] {
            day_start.add_days (-1),
            day_start
        };
        foreach (var cycle_day in days) {
            foreach (var slot in Schedule.slots_for_date (plan, cycle_day)) {
                var start = slot.start;
                var end = slot.end;
                if (end.compare (day_start) <= 0 ||
                    start.compare (day_end) >= 0) {
                    continue;
                }
                if (start.compare (day_start) < 0) {
                    start = day_start;
                }
                if (end.compare (day_end) > 0) {
                    end = day_end;
                }
                if (start.compare (end) < 0) {
                    segments += new TimelineSegment (start, end);
                }
            }
        }

        // Stable insertion sort by start time.
        for (int i = 1; i < segments.length; i++) {
            var item = segments[i];
            int j = i - 1;
            while (j >= 0 && segments[j].start.compare (item.start) > 0) {
                segments[j + 1] = segments[j];
                j--;
            }
            segments[j + 1] = item;
        }
        return segments;
    }

    private void refresh_segments () {
        segments = segments_for (plan, now, out day_start, out day_end);
    }

    private void draw (Gtk.DrawingArea area, Cairo.Context cr, int width, int height) {
        if (width <= 0) {
            return;
        }

        double bar_top = 6;
        double bar_height = 34;
        if (height < 62) {
            bar_height = double.max (18, height - 24);
        }

        Colors.muted (cr, 0.08);
        rounded_rect (cr, 0, bar_top, width, bar_height, 10);
        cr.fill ();

        GLib.TimeSpan day_length = day_end.difference (day_start);
        if (day_length <= 0) {
            return;
        }

        foreach (var segment in segments) {
            double x0 = ratio (segment.start, width, day_length);
            double x1 = ratio (segment.end, width, day_length);
            double segment_width = x1 - x0 - 2;
            if (segment_width < 1) {
                segment_width = 1;
            }
            Colors.accent (cr, Phase.EATING);
            rounded_rect (cr, x0 + 1, bar_top + 2, segment_width,
                bar_height - 4, 6);
            cr.fill ();
        }

        if (now.compare (day_start) >= 0 && now.compare (day_end) < 0) {
            double x = ratio (now, width, day_length);
            Colors.foreground (cr, 0.8);
            cr.rectangle (x - 1, bar_top - 3, 2, bar_height + 6);
            cr.fill ();
        }

        cr.select_font_face ("Sans", Cairo.FontSlant.NORMAL,
            Cairo.FontWeight.NORMAL);
        cr.set_font_size (10);
        string[] labels = { "00", "06", "12", "18", "24" };
        for (int i = 0; i < labels.length; i++) {
            Cairo.TextExtents extents;
            cr.text_extents (labels[i], out extents);
            double x;
            if (i == 0) {
                x = 0;
            } else if (i == labels.length - 1) {
                x = width - extents.width;
            } else {
                int hour = i * 6;
                var at = day_start.add_hours (hour);
                x = ratio (at, width, day_length) - extents.width / 2;
            }
            Colors.foreground (cr, 0.62);
            cr.move_to (x, bar_top + bar_height + 5 + extents.height);
            cr.show_text (labels[i]);
        }
    }

    private double ratio (GLib.DateTime time, int width, GLib.TimeSpan day_length) {
        double value = (double) time.difference (day_start) / (double) day_length;
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return width;
        }
        return value * width;
    }

    private static void rounded_rect (
            Cairo.Context cr, double x, double y,
            double width, double height, double radius) {
        double r = double.min (radius, double.min (width / 2, height / 2));
        cr.new_sub_path ();
        cr.arc (x + width - r, y + r, r, -GLib.Math.PI / 2, 0);
        cr.arc (x + width - r, y + height - r, r, 0, GLib.Math.PI / 2);
        cr.arc (x + r, y + height - r, r, GLib.Math.PI / 2, GLib.Math.PI);
        cr.arc (x + r, y + r, r, GLib.Math.PI, 3 * GLib.Math.PI / 2);
        cr.close_path ();
    }
}

}
