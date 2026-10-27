package store

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"com.comtudia.app/internal/plan"
)

// Store reads and writes a plan as JSON.
type Store struct {
	path string
}

// New returns a store backed by path.
func New(path string) *Store {
	return &Store{path: path}
}

const (
	configDir       = "comtudia"
	legacyConfigDir = "eatime"
)

// DefaultPath returns the per-user config path for comtudia.
func DefaultPath() (string, error) {
	return configPath(configDir)
}

// LegacyPath returns the config path used by the previous eatime name.
func LegacyPath() (string, error) {
	return configPath(legacyConfigDir)
}

func configPath(dirName string) (string, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("find user config directory: %w", err)
	}
	return filepath.Join(dir, dirName, "plan.json"), nil
}

// Path returns the file path used by the store.
func (s *Store) Path() string {
	return s.path
}

// LoadWithFallback loads the current plan, or migrates a plan from fallbackPath
// when the current file does not exist. The legacy file is left in place as a
// backup. It reports whether a migration occurred.
func (s *Store) LoadWithFallback(fallbackPath string) (plan.Plan, bool, error) {
	exists, err := fileExists(s.path)
	if err != nil {
		return plan.Plan{}, false, fmt.Errorf("check plan: %w", err)
	}
	if exists {
		p, err := s.Load()
		return p, false, err
	}
	if fallbackPath == "" || fallbackPath == s.path {
		p, err := s.Load()
		return p, false, err
	}

	legacyExists, err := fileExists(fallbackPath)
	if err != nil {
		return plan.Plan{}, false, fmt.Errorf("check legacy plan: %w", err)
	}
	if !legacyExists {
		p, err := s.Load()
		return p, false, err
	}

	p, err := New(fallbackPath).Load()
	if err != nil {
		return plan.Plan{}, false, err
	}
	if err := s.Save(p); err != nil {
		return p, false, fmt.Errorf("migrate plan: %w", err)
	}
	return p, true, nil
}

// Load reads a plan. A missing file returns the default plan.
func (s *Store) Load() (plan.Plan, error) {
	data, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return plan.Default(), nil
	}
	if err != nil {
		return plan.Plan{}, fmt.Errorf("read plan: %w", err)
	}

	p := plan.Default()
	if err := json.Unmarshal(data, &p); err != nil {
		return plan.Plan{}, fmt.Errorf("parse plan: %w", err)
	}
	if p.Version == 0 {
		p.Version = plan.Version
	}
	if p.Version > plan.Version {
		return plan.Plan{}, fmt.Errorf("plan version %d is newer than supported version %d", p.Version, plan.Version)
	}
	if err := p.Validate(); err != nil {
		return plan.Plan{}, fmt.Errorf("invalid plan: %w", err)
	}
	return p, nil
}

// Save validates and atomically writes a plan.
func (s *Store) Save(p plan.Plan) error {
	if err := p.Validate(); err != nil {
		return err
	}
	p.Version = plan.Version

	data, err := json.MarshalIndent(p, "", "  ")
	if err != nil {
		return fmt.Errorf("encode plan: %w", err)
	}
	data = append(data, '\n')

	dir := filepath.Dir(s.path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create config directory: %w", err)
	}

	tmp, err := os.CreateTemp(dir, ".plan-*.tmp")
	if err != nil {
		return fmt.Errorf("create temporary plan file: %w", err)
	}
	tmpName := tmp.Name()
	cleanup := func() {
		_ = tmp.Close()
		_ = os.Remove(tmpName)
	}

	if err := tmp.Chmod(0o600); err != nil {
		cleanup()
		return fmt.Errorf("secure temporary plan file: %w", err)
	}
	if _, err := tmp.Write(data); err != nil {
		cleanup()
		return fmt.Errorf("write plan: %w", err)
	}
	if err := tmp.Sync(); err != nil {
		cleanup()
		return fmt.Errorf("sync plan: %w", err)
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("close plan: %w", err)
	}
	if err := os.Rename(tmpName, s.path); err != nil {
		// Windows does not replace an existing file with Rename.
		if removeErr := os.Remove(s.path); removeErr == nil {
			if retryErr := os.Rename(tmpName, s.path); retryErr == nil {
				return nil
			}
		}
		_ = os.Remove(tmpName)
		return fmt.Errorf("replace plan file: %w", err)
	}
	return nil
}

func fileExists(path string) (bool, error) {
	_, err := os.Stat(path)
	if err == nil {
		return true, nil
	}
	if errors.Is(err, os.ErrNotExist) {
		return false, nil
	}
	return false, err
}
