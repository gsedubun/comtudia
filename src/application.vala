namespace Comtudia {

public class Application : Adw.Application {
    public const string APP_ID = "com.comtudia.app";

    private Store store;
    private Plan plan;
    private string? load_error = null;
    private Window? window = null;
    private Notifier notifier = new Notifier ();
    private TrayItem? tray = null;
    private uint tick_id = 0;

    public Application () {
        Object (
            application_id: APP_ID,
            flags: ApplicationFlags.DEFAULT_FLAGS);
    }

    public override void startup () {
        base.startup ();

        var style = Adw.StyleManager.get_default ();
        if (style != null) {
            style.set_color_scheme (Adw.ColorScheme.DEFAULT);
        }
        load_css ();
        ensure_icon_installed ();
        load_plan ();
        setup_actions ();
        setup_tray ();
        refresh_and_prime ();

        tick_id = GLib.Timeout.add_seconds (1, on_tick);
    }

    public override void activate () {
        ensure_window ();
        if (window != null) {
            window.present_window ();
        }
    }

    public override void shutdown () {
        if (tick_id != 0) {
            GLib.Source.remove (tick_id);
            tick_id = 0;
        }
        if (tray != null) {
            tray.close ();
            tray = null;
        }
        base.shutdown ();
    }

    public Plan current_plan () {
        return plan;
    }

    public bool tray_available () {
        return tray != null;
    }

    public void show_window () {
        ensure_window ();
        if (window != null) {
            window.present_window ();
        }
    }

    public string? save_plan (Plan next) {
        try {
            store.save (next);
        } catch (StoreError e) {
            return e.message;
        }
        plan = next;
        notifier.reset ();
        if (window != null) {
            window.set_plan (plan);
            window.close_settings ();
        }
        refresh_and_prime ();
        return null;
    }

    public void send_test_notification () {
        send_message (new Message (
            "comtudia", "Notifications are working."));
    }

    public void quit_app () {
        if (tick_id != 0) {
            GLib.Source.remove (tick_id);
            tick_id = 0;
        }
        if (tray != null) {
            tray.close ();
            tray = null;
        }
        quit ();
    }

    private void ensure_window () {
        if (window != null) {
            return;
        }
        window = new Window (this, plan, load_error, tray_available ());
        refresh ();
    }

    private void ensure_icon_installed () {
        var directory = GLib.Path.build_filename (
            GLib.Environment.get_user_data_dir (),
            "icons", "hicolor", "scalable", "apps");
        var path = GLib.Path.build_filename (directory, APP_ID + ".svg");
        if (GLib.FileUtils.test (path, GLib.FileTest.EXISTS)) {
            return;
        }
        try {
            GLib.File.new_for_path (directory).make_directory_with_parents (null);
            var bytes = GLib.resources_lookup_data (
                "/com/comtudia/app/com.comtudia.app.svg",
                GLib.ResourceLookupFlags.NONE);
            var data = bytes.get_data ();
            if (data != null) {
                GLib.FileUtils.set_contents (
                    path, (string) data, (ssize_t) bytes.get_size ());
            }
        } catch (GLib.Error e) {
            GLib.warning ("comtudia: cannot install icon: %s", e.message);
        }
    }

    private void load_css () {
        var provider = new Gtk.CssProvider ();
        provider.load_from_resource ("/com/comtudia/app/style.css");
        var display = Gdk.Display.get_default ();
        if (display != null) {
            Gtk.StyleContext.add_provider_for_display (
                display, provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION);
        }
    }

    private void load_plan () {
        store = new Store (Store.default_path ());
        try {
            bool migrated = false;
            plan = store.load_with_fallback (Store.legacy_path (), out migrated);
            if (migrated) {
                GLib.message ("comtudia: migrated plan from %s",
                    Store.legacy_path ());
            }
        } catch (StoreError e) {
            load_error = e.message;
            plan = Plan.create_default ();
            GLib.warning ("comtudia: %s", e.message);
        }
    }

    private void setup_actions () {
        var quit_action = new GLib.SimpleAction ("quit", null);
        quit_action.activate.connect (() => quit_app ());
        add_action (quit_action);

        var show_action = new GLib.SimpleAction ("show", null);
        show_action.activate.connect (() => show_window ());
        add_action (show_action);

        var settings_action = new GLib.SimpleAction ("settings", null);
        settings_action.activate.connect (() => {
            show_window ();
            if (window != null) {
                window.show_settings ();
            }
        });
        add_action (settings_action);

        var test_action = new GLib.SimpleAction ("test-notification", null);
        test_action.activate.connect (() => send_test_notification ());
        add_action (test_action);
    }

    private void setup_tray () {
        tray = TrayItem.create (() => show_window (), () => quit_app ());
    }

    private void refresh_and_prime () {
        var state = compute_now ();
        if (state == null) {
            return;
        }
        notifier.update (state);
        update_views (state);
    }

    private void refresh () {
        var state = compute_now ();
        if (state == null) {
            return;
        }
        update_views (state);
    }

    private State? compute_now () {
        try {
            return Schedule.compute (plan, new GLib.DateTime.now_local ());
        } catch (PlanError e) {
            GLib.warning ("comtudia: %s", e.message);
            return null;
        }
    }

    private void update_views (State state) {
        if (window != null) {
            window.update (state);
        }
        if (tray != null) {
            tray.update (state);
        }
    }

    private bool on_tick () {
        var state = compute_now ();
        if (state == null) {
            return true;
        }
        if (plan.notifications) {
            var message = notifier.update (state);
            if (message != null) {
                send_message (message);
            }
        }
        update_views (state);
        return true;
    }

    private void send_message (Message message) {
        var notification = new GLib.Notification (message.title);
        notification.set_body (message.body);
        notification.set_icon (new GLib.ThemedIcon (APP_ID));
        send_notification ("comtudia", notification);
    }
}

}
