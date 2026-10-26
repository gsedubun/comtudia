package store

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"com.comtudia.app/internal/plan"
)

func TestLoadMissingReturnsDefault(t *testing.T) {
	s := New(filepath.Join(t.TempDir(), "nested", "plan.json"))
	p, err := s.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if p != plan.Default() {
		t.Fatalf("plan = %+v, want default", p)
	}
}

func TestSaveLoadRoundTrip(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "comtudia", "plan.json")
	s := New(path)

	want := plan.Default()
	want.EatingMinutes = 8 * 60
	want.Meals = 2
	want.GapMinutes = 30
	want.WindowStartMinutes = 9 * 60
	want.Notifications = false
	want.RunInTray = true

	if err := s.Save(want); err != nil {
		t.Fatalf("Save: %v", err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("Stat: %v", err)
	}
	if info.Mode().Perm()&0o077 != 0 {
		t.Fatalf("plan permissions = %o, want no group/other access", info.Mode().Perm())
	}

	got, err := s.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestLoadCorrupt(t *testing.T) {
	path := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(path, []byte("{nope"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := New(path).Load(); err == nil {
		t.Fatal("expected parse error")
	}
}

func TestLoadInvalid(t *testing.T) {
	path := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(path, []byte(`{"eating_minutes": 1440, "meals": 3, "window_start_minutes": 720}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := New(path).Load(); err == nil {
		t.Fatal("expected validation error")
	}
}

func TestLoadPartialUsesDefaults(t *testing.T) {
	path := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(path, []byte(`{"eating_minutes": 480}`), 0o600); err != nil {
		t.Fatal(err)
	}
	p, err := New(path).Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if p.EatingMinutes != 480 || p.Meals != plan.DefaultMeals || p.WindowStartMinutes != plan.DefaultWindowStart {
		t.Fatalf("partial plan = %+v", p)
	}
	if p.Version != plan.Version {
		t.Fatalf("version = %d, want %d", p.Version, plan.Version)
	}
}

func TestSaveInvalid(t *testing.T) {
	s := New(filepath.Join(t.TempDir(), "plan.json"))
	if err := s.Save(plan.Plan{}); err == nil {
		t.Fatal("expected validation error")
	}
	if _, err := os.Stat(s.Path()); !os.IsNotExist(err) {
		t.Fatalf("invalid save created a file: %v", err)
	}
}

func TestNewerVersionRejected(t *testing.T) {
	path := filepath.Join(t.TempDir(), "plan.json")
	data := `{"version": 99, "eating_minutes": 360, "meals": 3, "window_start_minutes": 720}`
	if err := os.WriteFile(path, []byte(data), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := New(path).Load(); err == nil {
		t.Fatal("expected version error")
	}
}

func TestDefaultAndLegacyPaths(t *testing.T) {
	if runtime.GOOS != "linux" {
		t.Skip("XDG_CONFIG_HOME is Linux-specific")
	}
	dir := t.TempDir()
	t.Setenv("XDG_CONFIG_HOME", dir)

	current, err := DefaultPath()
	if err != nil {
		t.Fatal(err)
	}
	if want := filepath.Join(dir, "comtudia", "plan.json"); current != want {
		t.Fatalf("DefaultPath = %q, want %q", current, want)
	}

	legacy, err := LegacyPath()
	if err != nil {
		t.Fatal(err)
	}
	if want := filepath.Join(dir, "eatime", "plan.json"); legacy != want {
		t.Fatalf("LegacyPath = %q, want %q", legacy, want)
	}
}

func TestLoadWithFallbackMigratesLegacyPlan(t *testing.T) {
	dir := t.TempDir()
	currentPath := filepath.Join(dir, "comtudia", "plan.json")
	legacyPath := filepath.Join(dir, "eatime", "plan.json")

	legacyPlan := plan.Default()
	legacyPlan.GapMinutes = 60
	legacyPlan.RunInTray = true
	if err := New(legacyPath).Save(legacyPlan); err != nil {
		t.Fatal(err)
	}

	p, migrated, err := New(currentPath).LoadWithFallback(legacyPath)
	if err != nil {
		t.Fatalf("LoadWithFallback: %v", err)
	}
	if !migrated {
		t.Fatal("expected migration")
	}
	if p != legacyPlan {
		t.Fatalf("plan = %+v, want %+v", p, legacyPlan)
	}
	if _, err := os.Stat(currentPath); err != nil {
		t.Fatalf("migrated plan missing: %v", err)
	}
	if _, err := os.Stat(legacyPath); err != nil {
		t.Fatalf("legacy plan should be preserved: %v", err)
	}

	got, err := New(currentPath).Load()
	if err != nil {
		t.Fatal(err)
	}
	if got != legacyPlan {
		t.Fatalf("reloaded plan = %+v, want %+v", got, legacyPlan)
	}
}

func TestLoadWithFallbackPrefersCurrentPlan(t *testing.T) {
	dir := t.TempDir()
	currentPath := filepath.Join(dir, "comtudia", "plan.json")
	legacyPath := filepath.Join(dir, "eatime", "plan.json")

	currentPlan := plan.Default()
	currentPlan.EatingMinutes = 8 * 60
	currentPlan.Meals = 2
	legacyPlan := plan.Default()
	legacyPlan.GapMinutes = 60
	if err := New(currentPath).Save(currentPlan); err != nil {
		t.Fatal(err)
	}
	if err := New(legacyPath).Save(legacyPlan); err != nil {
		t.Fatal(err)
	}

	p, migrated, err := New(currentPath).LoadWithFallback(legacyPath)
	if err != nil {
		t.Fatal(err)
	}
	if migrated {
		t.Fatal("did not expect migration")
	}
	if p != currentPlan {
		t.Fatalf("plan = %+v, want current %+v", p, currentPlan)
	}
}

func TestLoadWithFallbackNoFiles(t *testing.T) {
	dir := t.TempDir()
	p, migrated, err := New(filepath.Join(dir, "comtudia", "plan.json")).LoadWithFallback(filepath.Join(dir, "eatime", "plan.json"))
	if err != nil {
		t.Fatal(err)
	}
	if migrated {
		t.Fatal("did not expect migration")
	}
	if p != plan.Default() {
		t.Fatalf("plan = %+v, want default", p)
	}
}

func TestLoadWithFallbackCorruptLegacy(t *testing.T) {
	dir := t.TempDir()
	currentPath := filepath.Join(dir, "comtudia", "plan.json")
	legacyPath := filepath.Join(dir, "eatime", "plan.json")
	if err := os.MkdirAll(filepath.Dir(legacyPath), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(legacyPath, []byte("{nope"), 0o600); err != nil {
		t.Fatal(err)
	}

	if _, _, err := New(currentPath).LoadWithFallback(legacyPath); err == nil {
		t.Fatal("expected legacy parse error")
	}
	if _, err := os.Stat(currentPath); !os.IsNotExist(err) {
		t.Fatalf("corrupt legacy plan should not create current plan: %v", err)
	}
}

func TestLoadWithFallbackCorruptCurrentDoesNotUseLegacy(t *testing.T) {
	dir := t.TempDir()
	currentPath := filepath.Join(dir, "comtudia", "plan.json")
	legacyPath := filepath.Join(dir, "eatime", "plan.json")
	if err := os.MkdirAll(filepath.Dir(currentPath), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(currentPath, []byte("{nope"), 0o600); err != nil {
		t.Fatal(err)
	}
	legacyPlan := plan.Default()
	legacyPlan.GapMinutes = 60
	if err := New(legacyPath).Save(legacyPlan); err != nil {
		t.Fatal(err)
	}

	if _, migrated, err := New(currentPath).LoadWithFallback(legacyPath); err == nil || migrated {
		t.Fatal("expected current plan error without migration")
	}
}
