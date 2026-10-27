/**
 * Schedule math check for the band app. Run with:
 *   node band/comtudia/test/schedule-check.js
 * Exits non-zero on a failed assertion.
 */
var assert = require('assert')
var plan = require('../src/common/plan.js')
var format = require('../src/common/format.js')

function at(h, m) {
  return new Date(2026, 9, 27, h, m, 0, 0)
}

function dayOffset(date, now) {
  var a = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())
  var b = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((a - b) / 86400000)
}

function when(date, now) {
  var d = dayOffset(date, now)
  var day = d === 0 ? 'today' : d === -1 ? 'yesterday' : d === 1 ? 'tomorrow' : 'day' + (d > 0 ? '+' : '') + d
  return day + ' ' + format.clock(date)
}

function check(label, p, now, expected) {
  var st = plan.compute(p, now)
  assert.strictEqual(st.phase, expected.phase)
  assert.strictEqual(format.clock(st.phaseStart), expected.start)
  assert.strictEqual(format.clock(st.phaseEnd), expected.end)
  assert.strictEqual(dayOffset(st.phaseStart, now), expected.startDay)
  assert.strictEqual(dayOffset(st.phaseEnd, now), expected.endDay)

  console.log(label)
  console.log('  phase      ' + st.phase)
  console.log('  phase      ' + when(st.phaseStart, now) + ' -> ' + when(st.phaseEnd, now))
  console.log('  next       ' + when(st.phaseEnd, now) + ' (in ' + format.duration(st.remainingMs) + ')')
  console.log('  next meal  ' + when(st.nextMeal.start, now))
  console.log('  progress   ' + Math.round(st.progress * 100) + '%')
  console.log('')
  return st
}

check('1) 18:6, 3 meals, start 12:00, at 13:00', {
  version: 1, eating_minutes: 360, meals: 3, gap_minutes: 0, window_start_minutes: 720
}, at(13, 0), {
  phase: 'eating', start: '12:00', end: '18:00', startDay: 0, endDay: 0
})

check('2) 16:8, 2 meals, gap 60, start 12:00, at 10:00', {
  version: 1, eating_minutes: 480, meals: 2, gap_minutes: 60, window_start_minutes: 720
}, at(10, 0), {
  phase: 'fasting', start: '21:00', end: '12:00', startDay: -1, endDay: 0
})

check('3) 6h, 3 meals, start 22:00, at 01:00', {
  version: 1, eating_minutes: 360, meals: 3, gap_minutes: 0, window_start_minutes: 1320
}, at(1, 0), {
  phase: 'eating', start: '22:00', end: '04:00', startDay: -1, endDay: 0
})

check('4) 20:4, 2 meals, gap 30, start 18:00, at 17:00', {
  version: 1, eating_minutes: 240, meals: 2, gap_minutes: 30, window_start_minutes: 1080
}, at(17, 0), {
  phase: 'fasting', start: '22:30', end: '18:00', startDay: -1, endDay: 0
})

// Exact boundaries: eating starts at 12:00, fasting starts at 18:00.
check('5) 18:6 at exactly 12:00 (eating starts)', {
  version: 1, eating_minutes: 360, meals: 3, gap_minutes: 0, window_start_minutes: 720
}, at(12, 0), {
  phase: 'eating', start: '12:00', end: '18:00', startDay: 0, endDay: 0
})

check('6) 18:6 at exactly 18:00 (fasting starts)', {
  version: 1, eating_minutes: 360, meals: 3, gap_minutes: 0, window_start_minutes: 720
}, at(18, 0), {
  phase: 'fasting', start: '18:00', end: '12:00', startDay: 0, endDay: 1
})

// Leftover minutes go to the earliest meals.
var ranges = plan.mealRanges({
  version: 1, eating_minutes: 400, meals: 3, gap_minutes: 0, window_start_minutes: 720
})
assert.deepStrictEqual(ranges.map(function (r) { return r.duration_minutes }), [134, 133, 133])
console.log('7) 400m split over 3 meals -> ' + ranges.map(function (r) {
  return r.duration_minutes + 'm'
}).join(', '))

// Validation: a 23h+ spread is rejected, 23h is allowed.
assert.ok(plan.validate({
  version: 1, eating_minutes: 360, meals: 2, gap_minutes: 1080, window_start_minutes: 720
}))
assert.strictEqual(plan.validate({
  version: 1, eating_minutes: 360, meals: 2, gap_minutes: 1020, window_start_minutes: 720
}), null)
console.log('8) span 24h rejected, span 23h accepted')

// Corrupt storage falls back to the default 18:6 plan.
assert.deepStrictEqual(plan.parse('{not json'), plan.defaultPlan())
console.log('9) corrupt stored JSON -> default 18:6, 3 meals, 12:00')

// Valid storage survives a JSON round-trip.
var stored = {
  version: 1, eating_minutes: 480, meals: 2, gap_minutes: 60, window_start_minutes: 720
}
assert.deepStrictEqual(plan.parse(JSON.stringify(stored)), stored)
console.log('10) stored JSON round-trip -> 16:8, 2 meals, gap 60')
console.log('')
console.log('All schedule checks passed.')
