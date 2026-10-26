namespace Comtudia {

/**
 * Shared Cairo colours for the ring and timeline. GTK uses the matching CSS
 * classes in data/style.css.
 */
public class Colors : GLib.Object {
    public static bool dark () {
        var manager = Adw.StyleManager.get_default ();
        return manager != null && manager.dark;
    }

    public static void accent (Cairo.Context cr, Phase phase) {
        bool is_dark = dark ();
        if (phase == Phase.EATING) {
            if (is_dark) {
                cr.set_source_rgba (0.239, 0.835, 0.592, 1.0);
            } else {
                cr.set_source_rgba (0.121, 0.616, 0.420, 1.0);
            }
        } else {
            if (is_dark) {
                cr.set_source_rgba (0.541, 0.635, 1.0, 1.0);
            } else {
                cr.set_source_rgba (0.310, 0.427, 0.910, 1.0);
            }
        }
    }

    public static void foreground (Cairo.Context cr, double alpha) {
        if (dark ()) {
            cr.set_source_rgba (1.0, 1.0, 1.0, alpha);
        } else {
            cr.set_source_rgba (0.0, 0.0, 0.0, alpha);
        }
    }

    public static void muted (Cairo.Context cr, double alpha) {
        if (dark ()) {
            cr.set_source_rgba (1.0, 1.0, 1.0, alpha);
        } else {
            cr.set_source_rgba (0.0, 0.0, 0.0, alpha);
        }
    }
}

}
