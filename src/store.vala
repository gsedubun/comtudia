namespace Comtudia {

public errordomain StoreError {
    IO,
    PARSE,
    INVALID,
    VERSION
}

/**
 * Reads and writes a plan as JSON. Writes are atomic and private.
 */
public class Store : GLib.Object {
    private const string CONFIG_DIR = "comtudia";
    private const string LEGACY_CONFIG_DIR = "eatime";

    public string path { get; private set; }

    public Store (string path) {
        this.path = path;
    }

    public static string default_path () {
        return config_path (CONFIG_DIR);
    }

    public static string legacy_path () {
        return config_path (LEGACY_CONFIG_DIR);
    }

    private static string config_path (string dir_name) {
        string prefix = GLib.Environment.get_variable ("XDG_CONFIG_HOME");
        if (prefix == null || prefix == "") {
            prefix = GLib.Path.build_filename (
                GLib.Environment.get_home_dir (), ".config");
        }
        return GLib.Path.build_filename (prefix, dir_name, "plan.json");
    }

    public static bool file_exists (string path) {
        return GLib.FileUtils.test (path, GLib.FileTest.EXISTS);
    }

    /**
     * Loads the current plan, migrating a plan from fallback_path when the
     * current file does not exist. The legacy file is left in place as a
     * backup.
     */
    public Plan load_with_fallback (string fallback_path, out bool migrated)
            throws StoreError {
        migrated = false;
        if (file_exists (path)) {
            return load ();
        }
        if (fallback_path == "" || fallback_path == path) {
            return load ();
        }
        if (!file_exists (fallback_path)) {
            return load ();
        }

        var legacy = new Store (fallback_path);
        var plan = legacy.load ();
        save (plan);
        migrated = true;
        return plan;
    }

    /**
     * Reads a plan. A missing file returns the default plan.
     */
    public Plan load () throws StoreError {
        if (!file_exists (path)) {
            return Plan.create_default ();
        }

        string data;
        try {
            GLib.FileUtils.get_contents (path, out data);
        } catch (GLib.FileError e) {
            if (e is GLib.FileError.NOENT) {
                return Plan.create_default ();
            }
            throw new StoreError.IO ("read plan: " + e.message);
        }
        return parse (data);
    }

    private Plan parse (string data) throws StoreError {
        var parser = new Json.Parser ();
        try {
            parser.load_from_data (data);
        } catch (GLib.Error e) {
            throw new StoreError.PARSE ("parse plan: " + e.message);
        }

        var root = parser.get_root ();
        if (root == null || root.get_node_type () != Json.NodeType.OBJECT) {
            throw new StoreError.PARSE ("parse plan: expected a JSON object");
        }
        var object = root.get_object ();
        if (object == null) {
            throw new StoreError.PARSE ("parse plan: expected a JSON object");
        }

        var plan = Plan.create_default ();
        if (object.has_member ("version")) {
            plan.version = (int) object.get_int_member ("version");
        }
        if (object.has_member ("eating_minutes")) {
            plan.eating_minutes = (int) object.get_int_member ("eating_minutes");
        }
        if (object.has_member ("meals")) {
            plan.meals = (int) object.get_int_member ("meals");
        }
        if (object.has_member ("gap_minutes")) {
            plan.gap_minutes = (int) object.get_int_member ("gap_minutes");
        }
        if (object.has_member ("window_start_minutes")) {
            plan.window_start_minutes =
                (int) object.get_int_member ("window_start_minutes");
        }
        if (object.has_member ("notifications")) {
            plan.notifications = object.get_boolean_member ("notifications");
        }
        if (object.has_member ("run_in_tray")) {
            plan.run_in_tray = object.get_boolean_member ("run_in_tray");
        }

        if (plan.version == 0) {
            plan.version = Plan.VERSION;
        }
        if (plan.version > Plan.VERSION) {
            throw new StoreError.VERSION (
                "plan version %d is newer than supported version %d".printf (
                    plan.version, Plan.VERSION));
        }

        try {
            plan.validate ();
        } catch (PlanError e) {
            throw new StoreError.INVALID ("invalid plan: " + e.message);
        }
        return plan;
    }

    /**
     * Validates and atomically writes a plan.
     */
    public void save (Plan plan) throws StoreError {
        try {
            plan.validate ();
        } catch (PlanError e) {
            throw new StoreError.INVALID (e.message);
        }
        plan.version = Plan.VERSION;

        var data = encode (plan) + "\n";
        var dir = GLib.File.new_for_path (GLib.Path.get_dirname (path));
        try {
            dir.make_directory_with_parents (null);
        } catch (GLib.Error e) {
            if (!(e is GLib.IOError.EXISTS)) {
                throw new StoreError.IO (
                    "create config directory: " + e.message);
            }
        }
        try {
            dir.set_attribute_uint32 (
                "unix::mode", 0700, GLib.FileQueryInfoFlags.NONE);
        } catch (GLib.Error e) {
            // Permission tweaks are best effort.
        }

        var file = GLib.File.new_for_path (path);
        try {
            string? etag = null;
            file.replace_contents (
                data.data,
                null,
                false,
                GLib.FileCreateFlags.PRIVATE,
                out etag);
            file.set_attribute_uint32 (
                "unix::mode", 0600, GLib.FileQueryInfoFlags.NONE);
        } catch (GLib.Error e) {
            throw new StoreError.IO ("write plan: " + e.message);
        }
    }

    private static string encode (Plan plan) {
        var builder = new Json.Builder ();
        builder.begin_object ();
        builder.set_member_name ("version");
        builder.add_int_value (Plan.VERSION);
        builder.set_member_name ("eating_minutes");
        builder.add_int_value (plan.eating_minutes);
        builder.set_member_name ("meals");
        builder.add_int_value (plan.meals);
        builder.set_member_name ("gap_minutes");
        builder.add_int_value (plan.gap_minutes);
        builder.set_member_name ("window_start_minutes");
        builder.add_int_value (plan.window_start_minutes);
        builder.set_member_name ("notifications");
        builder.add_boolean_value (plan.notifications);
        builder.set_member_name ("run_in_tray");
        builder.add_boolean_value (plan.run_in_tray);
        builder.end_object ();

        var generator = new Json.Generator ();
        generator.set_pretty (true);
        generator.set_indent (2);
        generator.set_root (builder.get_root ());
        size_t length;
        return generator.to_data (out length);
    }
}

}
