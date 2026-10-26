project('comtudia', ['vala', 'c'], version: '0.1.0', meson_version: '>=0.59')

gnome = import('gnome')

gtk4 = dependency('gtk4', version: '>=4.18')
adw = dependency('libadwaita-1', version: '>=1.6')
json_glib = dependency('json-glib-1.0')
gio = dependency('gio-2.0')

vala_args = ['--target-glib=2.80']

core_sources = files(
  'src/plan.vala',
  'src/schedule.vala',
  'src/format.vala',
  'src/store.vala',
  'src/notifier.vala',
)

ui_sources = files(
  'src/colors.vala',
  'src/ring.vala',
  'src/timeline.vala',
  'src/dashboard.vala',
  'src/settings.vala',
  'src/tray-item.vala',
  'src/window.vala',
  'src/application.vala',
  'src/main.vala',
)

resources = gnome.compile_resources(
  'comtudia-resources',
  'data/com.comtudia.app.gresource.xml',
  source_dir: 'data',
  c_name: 'comtudia',
)

executable('comtudia',
  core_sources + ui_sources + resources,
  dependencies: [gtk4, adw, json_glib, gio],
  vala_args: vala_args,
  install: true,
)

install_data(
  'data/com.comtudia.app.desktop',
  install_dir: join_paths(get_option('datadir'), 'applications'),
)
install_data(
  'data/com.comtudia.app.metainfo.xml',
  install_dir: join_paths(get_option('datadir'), 'metainfo'),
)
install_data(
  'data/com.comtudia.app.svg',
  install_dir: join_paths(
    get_option('datadir'), 'icons', 'hicolor', 'scalable', 'apps'),
)

plan_test = executable('plan-test',
  ['tests/plan-test.vala'] + core_sources,
  dependencies: [gio, json_glib],
  vala_args: vala_args,
)
test('plan', plan_test)

schedule_test = executable('schedule-test',
  ['tests/schedule-test.vala'] + core_sources,
  dependencies: [gio, json_glib],
  vala_args: vala_args,
)
test('schedule', schedule_test)

format_test = executable('format-test',
  ['tests/format-test.vala'] + core_sources,
  dependencies: [gio, json_glib],
  vala_args: vala_args,
)
test('format', format_test)

store_test = executable('store-test',
  ['tests/store-test.vala'] + core_sources,
  dependencies: [gio, json_glib],
  vala_args: vala_args,
)
test('store', store_test)

notify_test = executable('notify-test',
  ['tests/notify-test.vala'] + core_sources,
  dependencies: [gio, json_glib],
  vala_args: vala_args,
)
test('notify', notify_test)

settings_test = executable('settings-test',
  ['tests/settings-test.vala', 'src/settings.vala'] + core_sources,
  dependencies: [gtk4, adw, json_glib, gio],
  vala_args: vala_args,
)
test('settings', settings_test)

timeline_test = executable('timeline-test',
  ['tests/timeline-test.vala', 'src/timeline.vala', 'src/colors.vala'] + core_sources,
  dependencies: [gtk4, adw, json_glib, gio],
  vala_args: vala_args,
)
test('timeline', timeline_test)

tray_test = executable('tray-test',
  ['tests/tray-test.vala', 'src/tray-item.vala'] + core_sources,
  dependencies: [gio, json_glib],
  vala_args: vala_args,
)
test('tray', tray_test)
