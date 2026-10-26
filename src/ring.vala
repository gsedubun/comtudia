namespace Comtudia {

/**
 * Countdown ring drawn with Cairo on a Gtk.DrawingArea.
 */
public class Ring : Gtk.DrawingArea {
    public double progress { get; set; default = 0.0; }
    public bool eating { get; set; default = false; }

    public Ring () {
        set_content_width (260);
        set_content_height (260);
        set_size_request (260, 260);
        set_hexpand (true);
        set_halign (Gtk.Align.CENTER);
        set_draw_func (draw);
    }

    public void set_state (double progress, bool eating) {
        this.progress = progress < 0 ? 0 : (progress > 1 ? 1 : progress);
        this.eating = eating;
        queue_draw ();
    }

    private void draw (Gtk.DrawingArea area, Cairo.Context cr, int width, int height) {
        double side = double.min (width, height);
        if (side < 8) {
            return;
        }
        double cx = width / 2.0;
        double cy = height / 2.0;
        double outer = side / 2.0 - 2.0;
        double thickness = double.max (9.0, side * 0.065);
        double radius = outer - thickness / 2.0;

        cr.set_line_width (thickness);
        cr.set_line_cap (Cairo.LineCap.ROUND);

        cr.new_path ();
        cr.arc (cx, cy, radius, 0, 2 * GLib.Math.PI);
        if (Colors.dark ()) {
            cr.set_source_rgba (1.0, 1.0, 1.0, 0.10);
        } else {
            cr.set_source_rgba (0.0, 0.0, 0.0, 0.06);
        }
        cr.stroke ();

        if (progress > 0) {
            cr.new_path ();
            cr.arc (cx, cy, radius,
                -GLib.Math.PI / 2,
                -GLib.Math.PI / 2 + progress * 2 * GLib.Math.PI);
            Colors.accent (cr, eating ? Phase.EATING : Phase.FASTING);
            cr.stroke ();
        }
    }
}

}
