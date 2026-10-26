namespace Comtudia {

public class Window : Adw.ApplicationWindow {
    private Application app;
    private bool tray_available;
    private bool allow_close = false;
    private Adw.NavigationView navigation;
    private Dashboard dashboard;
    private SettingsPage settings_page;

    public Window (Application app, Plan plan, string? load_error,
            bool tray_available) {
        Object (application: app);
        this.app = app;
        this.tray_available = tray_available;

        set_title ("comtudia");
        set_default_size (480, 860);
        set_size_request (360, 560);

        dashboard = new Dashboard (plan, load_error);
        dashboard.plan_requested.connect (show_settings);

        navigation = new Adw.NavigationView ();
        var dashboard_page = new Adw.NavigationPage (dashboard, "comtudia");
        navigation.add (dashboard_page);

        settings_page = new SettingsPage (
            plan,
            Store.default_path (),
            tray_available,
            (p) => app.save_plan (p),
            () => close_settings (),
            () => app.send_test_notification ());

        set_content (navigation);
        close_request.connect (on_close_request);
    }

    public void update (State state) {
        dashboard.update (state);
    }

    public void set_plan (Plan plan) {
        dashboard.set_plan (plan);
        settings_page.set_plan (plan);
    }

    public void show_settings () {
        settings_page.set_plan (app.current_plan ());
        if (navigation.get_visible_page () != settings_page.page) {
            navigation.push (settings_page.page);
        }
    }

    public void close_settings () {
        if (navigation.get_visible_page () == settings_page.page) {
            navigation.pop ();
        }
    }

    public void present_window () {
        present ();
    }

    private bool on_close_request () {
        if (allow_close) {
            return false;
        }
        var plan = app.current_plan ();
        if (plan.run_in_tray && tray_available && app.tray_available ()) {
            set_visible (false);
            return true;
        }
        allow_close = true;
        app.quit_app ();
        return false;
    }
}

}
