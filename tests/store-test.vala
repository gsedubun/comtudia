using Comtudia;

string temp_dir () {
    try {
        return GLib.DirUtils.make_tmp ("comtudia-test-XXXXXX");
    } catch (GLib.FileError e) {
        GLib.error ("make temp dir: %s", e.message);
    }
    return "";
}

void write_file (string path, string data) {
    var dir = GLib.Path.get_dirname (path);
    GLib.DirUtils.create_with_parents (dir, 0700);
    try {
        GLib.FileUtils.set_contents (path, data);
    } catch (GLib.FileError e) {
        GLib.error ("write %s: %s", path, e.message);
    }
}

string read_file (string path) {
    string data;
    try {
        GLib.FileUtils.get_contents (path, out data);
    } catch (GLib.FileError e) {
        GLib.error ("read %s: %s", path, e.message);
    }
    return data;
}

uint32 file_mode (string path) {
    try {
        var info = GLib.File.new_for_path (path).query_info (
            "unix::mode", GLib.FileQueryInfoFlags.NONE);
        return info.get_attribute_uint32 ("unix::mode");
    } catch (GLib.Error e) {
        GLib.error ("stat %s: %s", path, e.message);
    }
    return 0;
}

void assert_plan (Plan actual, Plan expected, string label) {
    if (!actual.equals_plan (expected)) {
        GLib.error ("%s: plan does not match expected values", label);
    }
}

void test_load_missing_returns_default () {
    var dir = temp_dir ();
    var store = new Store (GLib.Path.build_filename (
        dir, "nested", "plan.json"));
    var plan = store.load ();
    assert_plan (plan, Plan.create_default (), "missing plan");
}

void test_save_load_round_trip () {
    var dir = temp_dir ();
    var path = GLib.Path.build_filename (dir, "comtudia", "plan.json");
    var store = new Store (path);

    var want = Plan.create_default ();
    want.eating_minutes = 8 * 60;
    want.meals = 2;
    want.gap_minutes = 30;
    want.window_start_minutes = 9 * 60;
    want.notifications = false;
    want.run_in_tray = true;

    store.save (want);
    var mode = file_mode (path);
    if ((mode & 0077) != 0) {
        GLib.error ("plan permissions = %o, want no group/other access", mode);
    }
    var dir_mode = file_mode (GLib.Path.get_dirname (path));
    if ((dir_mode & 0077) != 0) {
        GLib.error ("config directory permissions = %o, want no group/other access", dir_mode);
    }

    var got = store.load ();
    assert_plan (got, want, "round trip");

    var second = want.copy ();
    second.gap_minutes = 45;
    store.save (second);
    assert_plan (store.load (), second, "atomic overwrite");

    try {
        var dir_handle = GLib.Dir.open (GLib.Path.get_dirname (path), 0);
        int entries = 0;
        while (dir_handle.read_name () != null) {
            entries++;
        }
        if (entries != 1) {
            GLib.error ("config directory has %d entries, want 1", entries);
        }
    } catch (GLib.FileError e) {
        GLib.error ("list config directory: %s", e.message);
    }
}

void test_load_corrupt () {
    var path = GLib.Path.build_filename (temp_dir (), "plan.json");
    write_file (path, "{nope");
    try {
        new Store (path).load ();
        GLib.error ("expected parse error");
    } catch (StoreError e) {
        GLib.assert_true (e is StoreError.PARSE);
    }
}

void test_load_invalid () {
    var path = GLib.Path.build_filename (temp_dir (), "plan.json");
    write_file (path,
        "{\"eating_minutes\": 1440, \"meals\": 3, \"window_start_minutes\": 720}");
    try {
        new Store (path).load ();
        GLib.error ("expected validation error");
    } catch (StoreError e) {
        GLib.assert_true (e is StoreError.INVALID);
    }
}

void test_load_partial_uses_defaults () {
    var path = GLib.Path.build_filename (temp_dir (), "plan.json");
    write_file (path, "{\"eating_minutes\": 480}");
    var plan = new Store (path).load ();
    GLib.assert_true (plan.eating_minutes == 480);
    GLib.assert_true (plan.meals == Plan.DEFAULT_MEALS);
    GLib.assert_true (plan.window_start_minutes == Plan.DEFAULT_WINDOW_START);
    GLib.assert_true (plan.version == Plan.VERSION);
}

void test_save_invalid () {
    var path = GLib.Path.build_filename (temp_dir (), "plan.json");
    var store = new Store (path);
    var invalid = new Plan ();
    invalid.meals = 0;
    try {
        store.save (invalid);
        GLib.error ("expected validation error");
    } catch (StoreError e) {
        GLib.assert_true (e is StoreError.INVALID);
    }
    GLib.assert_true (!Store.file_exists (path));
}

void test_newer_version_rejected () {
    var path = GLib.Path.build_filename (temp_dir (), "plan.json");
    write_file (path,
        "{\"version\": 99, \"eating_minutes\": 360, \"meals\": 3, " +
        "\"window_start_minutes\": 720}");
    try {
        new Store (path).load ();
        GLib.error ("expected version error");
    } catch (StoreError e) {
        GLib.assert_true (e is StoreError.VERSION);
    }
}

void test_default_and_legacy_paths () {
    var dir = temp_dir ();
    GLib.Environment.set_variable ("XDG_CONFIG_HOME", dir, true);
    var current = Store.default_path ();
    var legacy = Store.legacy_path ();
    GLib.assert_cmpstr (current, GLib.CompareOperator.EQ,
        GLib.Path.build_filename (dir, "comtudia", "plan.json"));
    GLib.assert_cmpstr (legacy, GLib.CompareOperator.EQ,
        GLib.Path.build_filename (dir, "eatime", "plan.json"));
}

void test_load_with_fallback_migrates_legacy () {
    var dir = temp_dir ();
    var current_path = GLib.Path.build_filename (
        dir, "comtudia", "plan.json");
    var legacy_path = GLib.Path.build_filename (
        dir, "eatime", "plan.json");

    var legacy_plan = Plan.create_default ();
    legacy_plan.gap_minutes = 60;
    legacy_plan.run_in_tray = true;
    new Store (legacy_path).save (legacy_plan);

    bool migrated;
    var plan = new Store (current_path).load_with_fallback (
        legacy_path, out migrated);
    GLib.assert_true (migrated);
    assert_plan (plan, legacy_plan, "migrated plan");
    GLib.assert_true (Store.file_exists (current_path));
    GLib.assert_true (Store.file_exists (legacy_path));

    var got = new Store (current_path).load ();
    assert_plan (got, legacy_plan, "reloaded plan");
}

void test_load_with_fallback_prefers_current () {
    var dir = temp_dir ();
    var current_path = GLib.Path.build_filename (
        dir, "comtudia", "plan.json");
    var legacy_path = GLib.Path.build_filename (
        dir, "eatime", "plan.json");

    var current_plan = Plan.create_default ();
    current_plan.eating_minutes = 8 * 60;
    current_plan.meals = 2;
    var legacy_plan = Plan.create_default ();
    legacy_plan.gap_minutes = 60;
    new Store (current_path).save (current_plan);
    new Store (legacy_path).save (legacy_plan);

    bool migrated;
    var plan = new Store (current_path).load_with_fallback (
        legacy_path, out migrated);
    GLib.assert_true (!migrated);
    assert_plan (plan, current_plan, "current plan");
}

void test_load_with_fallback_no_files () {
    var dir = temp_dir ();
    bool migrated;
    var plan = new Store (GLib.Path.build_filename (dir, "comtudia", "plan.json"))
        .load_with_fallback (
            GLib.Path.build_filename (dir, "eatime", "plan.json"),
            out migrated);
    GLib.assert_true (!migrated);
    assert_plan (plan, Plan.create_default (), "no files");
}

void test_load_with_fallback_corrupt_legacy () {
    var dir = temp_dir ();
    var current_path = GLib.Path.build_filename (dir, "comtudia", "plan.json");
    var legacy_path = GLib.Path.build_filename (dir, "eatime", "plan.json");
    write_file (legacy_path, "{nope");

    bool migrated;
    try {
        new Store (current_path).load_with_fallback (legacy_path, out migrated);
        GLib.error ("expected legacy parse error");
    } catch (StoreError e) {
        GLib.assert_true (e is StoreError.PARSE);
    }
    GLib.assert_true (!Store.file_exists (current_path));
}

void test_load_with_fallback_corrupt_current () {
    var dir = temp_dir ();
    var current_path = GLib.Path.build_filename (dir, "comtudia", "plan.json");
    var legacy_path = GLib.Path.build_filename (dir, "eatime", "plan.json");
    write_file (current_path, "{nope");

    var legacy_plan = Plan.create_default ();
    legacy_plan.gap_minutes = 60;
    new Store (legacy_path).save (legacy_plan);

    bool migrated = false;
    try {
        new Store (current_path).load_with_fallback (legacy_path, out migrated);
        GLib.error ("expected current plan error");
    } catch (StoreError e) {
        GLib.assert_true (e is StoreError.PARSE);
    }
    GLib.assert_true (!migrated);
}

int main (string[] args) {
    GLib.Test.init (ref args);
    GLib.Test.add_func ("/store/missing", test_load_missing_returns_default);
    GLib.Test.add_func ("/store/round-trip", test_save_load_round_trip);
    GLib.Test.add_func ("/store/corrupt", test_load_corrupt);
    GLib.Test.add_func ("/store/invalid", test_load_invalid);
    GLib.Test.add_func ("/store/partial", test_load_partial_uses_defaults);
    GLib.Test.add_func ("/store/save-invalid", test_save_invalid);
    GLib.Test.add_func ("/store/newer-version", test_newer_version_rejected);
    GLib.Test.add_func ("/store/paths", test_default_and_legacy_paths);
    GLib.Test.add_func ("/store/migrate", test_load_with_fallback_migrates_legacy);
    GLib.Test.add_func ("/store/prefer-current", test_load_with_fallback_prefers_current);
    GLib.Test.add_func ("/store/no-files", test_load_with_fallback_no_files);
    GLib.Test.add_func ("/store/corrupt-legacy",
        test_load_with_fallback_corrupt_legacy);
    GLib.Test.add_func ("/store/corrupt-current",
        test_load_with_fallback_corrupt_current);
    return GLib.Test.run ();
}
