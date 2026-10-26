namespace Comtudia {

public delegate void TrayCallback ();

[DBus (name = "org.kde.StatusNotifierItem")]
public class StatusNotifier : GLib.Object {
    private TrayItem owner;

    [DBus (name = "Category")]
    public string category { get; default = "ApplicationStatus"; }
    [DBus (name = "Id")]
    public string id { get; default = "com.comtudia.app"; }
    [DBus (name = "Title")]
    public string title { get; default = "comtudia"; }
    private string status_value = "Active";

    [DBus (name = "Status")]
    public string status {
        get { return status_value; }
    }
    [DBus (name = "WindowId")]
    public int window_id { get; default = 0; }
    [DBus (name = "IconName")]
    public string icon_name { get; default = "com.comtudia.app"; }
    private string icon_theme_path_value = "";

    [DBus (name = "IconThemePath")]
    public string icon_theme_path {
        get { return icon_theme_path_value; }
    }
    [DBus (name = "ItemIsMenu")]
    public bool item_is_menu { get; default = false; }

    [DBus (name = "IconPixmap", signature = "a(iiay)")]
    public GLib.Variant icon_pixmap {
        owned get {
            return new GLib.Variant.array (
                new GLib.VariantType ("(iiay)"), new GLib.Variant[0]);
        }
    }

    [DBus (name = "OverlayIconPixmap", signature = "a(iiay)")]
    public GLib.Variant overlay_icon_pixmap {
        owned get {
            return new GLib.Variant.array (
                new GLib.VariantType ("(iiay)"), new GLib.Variant[0]);
        }
    }

    [DBus (name = "AttentionIconPixmap", signature = "a(iiay)")]
    public GLib.Variant attention_icon_pixmap {
        owned get {
            return new GLib.Variant.array (
                new GLib.VariantType ("(iiay)"), new GLib.Variant[0]);
        }
    }

    [DBus (name = "ToolTip", signature = "(sa(iiay)ss)")]
    public GLib.Variant tool_tip {
        owned get {
            var icon = new GLib.Variant.array (
                new GLib.VariantType ("(iiay)"), new GLib.Variant[0]);
            return new GLib.Variant.tuple (new GLib.Variant[] {
                new GLib.Variant.string (""),
                icon,
                new GLib.Variant.string ("comtudia"),
                new GLib.Variant.string (owner.status_text_value ())
            });
        }
    }

    [DBus (name = "Menu")]
    public GLib.ObjectPath menu { owned get { return new GLib.ObjectPath ("/MenuBar"); } }

    [DBus (name = "NewTitle")]
    public signal void new_title ();
    [DBus (name = "NewIcon")]
    public signal void new_icon ();
    [DBus (name = "NewAttentionIcon")]
    public signal void new_attention_icon ();
    [DBus (name = "NewOverlayIcon")]
    public signal void new_overlay_icon ();
    [DBus (name = "NewToolTip")]
    public signal void new_tool_tip ();
    [DBus (name = "NewStatus")]
    public signal void new_status (string status);
    [DBus (name = "NewMenu")]
    public signal void new_menu ();

    public StatusNotifier (TrayItem owner) {
        this.owner = owner;
        icon_theme_path_value = GLib.Path.build_filename (
            GLib.Environment.get_user_data_dir (), "icons");
    }

    [DBus (name = "Activate")]
    public void activate (int x, int y) throws GLib.DBusError, GLib.IOError {
        owner.handle_show ();
    }

    [DBus (name = "SecondaryActivate")]
    public void secondary_activate (int x, int y)
            throws GLib.DBusError, GLib.IOError {
        owner.handle_show ();
    }

    [DBus (name = "ContextMenu")]
    public void context_menu (int x, int y)
            throws GLib.DBusError, GLib.IOError {
        owner.handle_show ();
    }

    [DBus (name = "Scroll")]
    public void scroll (int delta, string orientation)
            throws GLib.DBusError, GLib.IOError {
    }

    [DBus (name = "ProvideXdgActivationToken")]
    public void provide_xdg_activation_token (string token)
            throws GLib.DBusError, GLib.IOError {
    }

    internal void notify_status (string next) {
        if (status_value == next) {
            return;
        }
        status_value = next;
        new_status (next);
        new_tool_tip ();
    }
}

[DBus (name = "com.canonical.dbusmenu")]
public class DbusMenu : GLib.Object {
    private TrayItem owner;

    [DBus (name = "LayoutUpdated")]
    public signal void layout_updated (uint revision, int parent);

    public DbusMenu (TrayItem owner) {
        this.owner = owner;
    }

    [DBus (name = "GetLayout", signature = "(u(ia{sv}av))")]
    public GLib.Variant get_layout (
            int parent_id, int recursion_depth, string[] property_names) {
        return owner.menu_layout ();
    }

    [DBus (name = "GetGroupProperties", signature = "a(ia{sv})")]
    public GLib.Variant get_group_properties (
            int[] ids, string[] property_names) {
        return owner.menu_group_properties (ids);
    }

    [DBus (name = "GetProperty", signature = "v")]
    public GLib.Variant get_property (int id, string name) {
        return new GLib.Variant.variant (owner.menu_property (id, name));
    }

    [DBus (name = "Event")]
    public void event (int id, string event_id, GLib.Variant data,
            uint timestamp) throws GLib.DBusError, GLib.IOError {
        if (event_id == "clicked") {
            owner.handle_menu_click (id);
        }
    }

    [DBus (name = "AboutToShow")]
    public bool about_to_show (int id) throws GLib.DBusError, GLib.IOError {
        return false;
    }

    [DBus (name = "AboutToShowGroup", signature = "(aiai)")]
    public GLib.Variant about_to_show_group (int[] ids) {
        var empty = new GLib.Variant.array (
            new GLib.VariantType ("i"), new GLib.Variant[0]);
        return new GLib.Variant.tuple (new GLib.Variant[] { empty, empty });
    }

    public void notify_layout_updated (uint revision) {
        layout_updated (revision, 0);
    }
}

/**
 * A StatusNotifierItem plus DBusMenu implementation for the system tray.
 */
public class TrayItem : GLib.Object {
    private GLib.DBusConnection? connection = null;
    private StatusNotifier? sni = null;
    private DbusMenu? menu = null;
    private uint sni_registration = 0;
    private uint menu_registration = 0;
    private string? bus_name = null;
    private string status = "";
    private uint revision = 0;
    private TrayCallback on_show;
    private TrayCallback on_quit;

    private TrayItem (TrayCallback on_show, TrayCallback on_quit) {
        this.on_show = on_show;
        this.on_quit = on_quit;
        this.status = "Next eating soon";
    }

    public static bool watcher_available () {
        try {
            var bus = GLib.Bus.get_sync (GLib.BusType.SESSION);
            var reply = bus.call_sync (
                "org.freedesktop.DBus",
                "/org/freedesktop/DBus",
                "org.freedesktop.DBus",
                "NameHasOwner",
                new GLib.Variant.tuple (new GLib.Variant[] {
                    new GLib.Variant.string ("org.kde.StatusNotifierWatcher")
                }),
                new GLib.VariantType ("(b)"),
                GLib.DBusCallFlags.NONE,
                -1,
                null);
            bool owned = false;
            reply.get ("(b)", out owned);
            return owned;
        } catch (GLib.Error e) {
            return false;
        }
    }

    public static TrayItem? create (
            TrayCallback on_show, TrayCallback on_quit) {
        if (!watcher_available ()) {
            return null;
        }
        var item = new TrayItem (on_show, on_quit);
        if (!item.start ()) {
            return null;
        }
        return item;
    }

    private bool start () {
        try {
            connection = GLib.Bus.get_sync (GLib.BusType.SESSION);
        } catch (GLib.Error e) {
            GLib.warning ("comtudia: cannot connect to session bus: %s",
                e.message);
            return false;
        }
        if (connection == null) {
            return false;
        }

        sni = new StatusNotifier (this);
        menu = new DbusMenu (this);
        try {
            sni_registration = connection.register_object (
                "/StatusNotifierItem", sni);
            menu_registration = connection.register_object ("/MenuBar", menu);
        } catch (GLib.Error e) {
            GLib.warning ("comtudia: cannot register tray objects: %s",
                e.message);
            return false;
        }

        uint suffix = (uint) (new GLib.DateTime.now_local ().to_unix () & 0xffff);
        string requested_name = "org.kde.StatusNotifierItem-%u-1".printf (suffix);
        bus_name = requested_name;
        try {
            var reply = connection.call_sync (
                "org.freedesktop.DBus",
                "/org/freedesktop/DBus",
                "org.freedesktop.DBus",
                "RequestName",
                new GLib.Variant.tuple (new GLib.Variant[] {
                    new GLib.Variant.string (requested_name),
                    new GLib.Variant.uint32 (0)
                }),
                new GLib.VariantType ("(u)"),
                GLib.DBusCallFlags.NONE,
                -1,
                null);
            uint result = 0;
            reply.get ("(u)", out result);
            if (result != 1 && result != 4) {
                bus_name = connection.get_unique_name ();
            }
        } catch (GLib.Error e) {
            bus_name = connection.get_unique_name ();
        }
        if (bus_name == null) {
            return false;
        }

        try {
            connection.call_sync (
                "org.kde.StatusNotifierWatcher",
                "/StatusNotifierWatcher",
                "org.kde.StatusNotifierWatcher",
                "RegisterStatusNotifierItem",
                new GLib.Variant.tuple (new GLib.Variant[] {
                    new GLib.Variant.string (bus_name)
                }),
                null,
                GLib.DBusCallFlags.NONE,
                -1,
                null);
        } catch (GLib.Error e) {
            GLib.warning ("comtudia: cannot register tray item: %s",
                e.message);
            close ();
            return false;
        }
        return true;
    }

    public void update (State state) {
        string next = status_text (state);
        if (next == status) {
            return;
        }
        status = next;
        revision++;
        if (sni != null) {
            sni.notify_status ("Active");
        }
        if (menu != null) {
            menu.notify_layout_updated (revision);
        }
    }

    public void close () {
        if (connection != null) {
            if (sni_registration != 0) {
                connection.unregister_object (sni_registration);
                sni_registration = 0;
            }
            if (menu_registration != 0) {
                connection.unregister_object (menu_registration);
                menu_registration = 0;
            }
        }
        sni = null;
        menu = null;
        connection = null;
    }

    internal void handle_show () {
        on_show ();
    }

    internal void handle_quit () {
        on_quit ();
    }

    internal void handle_menu_click (int id) {
        if (id == 2) {
            on_show ();
        } else if (id == 3) {
            on_quit ();
        }
    }

    internal string status_text_value () {
        return status;
    }

    internal GLib.Variant menu_layout () {
        var children = new GLib.VariantBuilder (new GLib.VariantType ("av"));
        foreach (int id in new int[] { 1, 2, 3 }) {
            children.add_value (new GLib.Variant.variant (menu_item (id)));
        }
        var root_props = new GLib.VariantBuilder (
            new GLib.VariantType ("a{sv}"));
        var layout = new GLib.Variant.tuple (new GLib.Variant[] {
            new GLib.Variant.int32 (0),
            root_props.end (),
            children.end ()
        });
        return new GLib.Variant.tuple (new GLib.Variant[] {
            new GLib.Variant.uint32 (revision),
            layout
        });
    }

    internal GLib.Variant menu_item (int id) {
        var children = new GLib.VariantBuilder (new GLib.VariantType ("av"));
        return new GLib.Variant.tuple (new GLib.Variant[] {
            new GLib.Variant.int32 (id),
            menu_properties (id),
            children.end ()
        });
    }

    internal GLib.Variant menu_properties (int id) {
        var props = new GLib.VariantBuilder (new GLib.VariantType ("a{sv}"));
        switch (id) {
        case 1:
            props.add ("{sv}", "label", new GLib.Variant.string (status));
            props.add ("{sv}", "enabled", new GLib.Variant.boolean (false));
            break;
        case 2:
            props.add ("{sv}", "label",
                new GLib.Variant.string ("Show comtudia"));
            props.add ("{sv}", "enabled", new GLib.Variant.boolean (true));
            break;
        case 3:
            props.add ("{sv}", "label", new GLib.Variant.string ("Quit"));
            props.add ("{sv}", "enabled", new GLib.Variant.boolean (true));
            break;
        }
        return props.end ();
    }

    internal GLib.Variant menu_group_properties (int[] ids) {
        var result = new GLib.VariantBuilder (
            new GLib.VariantType ("a(ia{sv})"));
        foreach (int id in ids) {
            var item = new GLib.Variant.tuple (new GLib.Variant[] {
                new GLib.Variant.int32 (id),
                menu_properties (id)
            });
            result.add_value (item);
        }
        return result.end ();
    }

    internal GLib.Variant menu_property (int id, string name) {
        if (name == "label") {
            switch (id) {
            case 1: return new GLib.Variant.string (status);
            case 2: return new GLib.Variant.string ("Show comtudia");
            case 3: return new GLib.Variant.string ("Quit");
            }
        }
        if (name == "enabled") {
            return new GLib.Variant.boolean (id != 1);
        }
        if (name == "visible") {
            return new GLib.Variant.boolean (true);
        }
        return new GLib.Variant.string ("");
    }

    public static string status_text (State state) {
        if (state.phase == Phase.EATING) {
            return "Eating until " + Format.clock (state.phase_end);
        }
        return "Next eating in " + Format.duration (state.remaining);
    }
}

}
