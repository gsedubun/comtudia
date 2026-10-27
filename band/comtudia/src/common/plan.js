/**
 * Pure plan + schedule math, ported from the desktop app's internal/plan
 * package. No Vela APIs are used here so it can be tested with Node.
 *
 * Cycle semantics:
 * - 24 hour day anchored at local midnight.
 * - eating_minutes is split evenly; leftover minutes go to the earliest meals.
 * - gap_minutes is fasting between meals.
 * - contiguous meals form one eating phase.
 * - eating windows may cross midnight; lookup scans yesterday/today/tomorrow.
 */

var MINUTES_PER_DAY = 1440
var MAX_MEALS = 8
var MIN_MEAL_MINUTE = 15
var MIN_EATING = 60
var MIN_FASTING = 60

function defaultPlan() {
  return {
    version: 1,
    eating_minutes: 360,
    meals: 3,
    gap_minutes: 0,
    window_start_minutes: 720
  }
}

function copyPlan(p) {
  return {
    version: p.version,
    eating_minutes: p.eating_minutes,
    meals: p.meals,
    gap_minutes: p.gap_minutes,
    window_start_minutes: p.window_start_minutes
  }
}

function toInt(value, fallback) {
  var n = Number(value)
  if (!isFinite(n)) return fallback
  return Math.round(n)
}

function isInt(n) {
  return typeof n === 'number' && isFinite(n) && n === Math.floor(n)
}

/** Parses stored JSON. Missing, corrupt or invalid data returns the default. */
function parse(value) {
  if (!value) return defaultPlan()
  var raw = value
  if (typeof value === 'string') {
    try {
      raw = JSON.parse(value)
    } catch (e) {
      return defaultPlan()
    }
  }
  if (!raw || typeof raw !== 'object') return defaultPlan()

  var p = {
    version: 1,
    eating_minutes: toInt(raw.eating_minutes, -1),
    meals: toInt(raw.meals, -1),
    gap_minutes: toInt(raw.gap_minutes, -1),
    window_start_minutes: toInt(raw.window_start_minutes, -1)
  }
  if (validate(p)) return defaultPlan()
  return p
}

/** Returns an error string, or null when the plan is valid. */
function validate(p) {
  if (!p || typeof p !== 'object') return 'No plan'
  if (!isInt(p.meals) || p.meals < 1 || p.meals > MAX_MEALS) {
    return 'Meals must be 1-8'
  }
  if (!isInt(p.eating_minutes) || p.eating_minutes < MIN_EATING ||
      p.eating_minutes > MINUTES_PER_DAY - MIN_FASTING) {
    return 'Eating must be 1h-23h'
  }
  if (!isInt(p.window_start_minutes) || p.window_start_minutes < 0 ||
      p.window_start_minutes >= MINUTES_PER_DAY) {
    return 'Start must be 00:00-23:59'
  }
  if (!isInt(p.gap_minutes) || p.gap_minutes < 0) {
    return 'Gap cannot be negative'
  }
  if (p.meals === 1 && p.gap_minutes !== 0) {
    return 'One meal needs no gap'
  }
  if (Math.floor(p.eating_minutes / p.meals) < MIN_MEAL_MINUTE) {
    return 'Each meal needs 15m'
  }
  if (spanMinutes(p) > MINUTES_PER_DAY - MIN_FASTING) {
    return 'Meals must fit in 23h'
  }
  return null
}

function fastingMinutes(p) {
  return MINUTES_PER_DAY - p.eating_minutes
}

function spanMinutes(p) {
  if (p.meals <= 0) return 0
  return p.eating_minutes + (p.meals - 1) * p.gap_minutes
}

/** Equal meals, leftover minute to the earliest slots. */
function mealRanges(p) {
  if (p.meals <= 0 || p.eating_minutes < p.meals) return []
  var base = Math.floor(p.eating_minutes / p.meals)
  var remainder = p.eating_minutes % p.meals
  var out = []
  var offset = 0
  for (var i = 0; i < p.meals; i++) {
    var duration = base + (i < remainder ? 1 : 0)
    out.push({
      index: i + 1,
      start_minutes: offset,
      end_minutes: offset + duration,
      duration_minutes: duration
    })
    offset += duration + p.gap_minutes
  }
  return out
}

function dayStartOf(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0)
}

/** Wall-clock minute offset from local midnight, rolling into following days. */
function wallTime(dayStart, minutes) {
  var days = Math.floor(minutes / MINUTES_PER_DAY)
  var rem = minutes % MINUTES_PER_DAY
  var date = new Date(
    dayStart.getFullYear(),
    dayStart.getMonth(),
    dayStart.getDate() + days,
    0, 0, 0, 0
  )
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    Math.floor(rem / 60),
    rem % 60,
    0, 0
  )
}

/** Meals belonging to the cycle that starts on the given local day. */
function slotsForDate(p, day) {
  var ranges = mealRanges(p)
  if (!ranges.length) return []

  var dayStart = dayStartOf(day)
  var windowStart = wallTime(dayStart, p.window_start_minutes)
  var slots = []
  for (var i = 0; i < ranges.length; i++) {
    var r = ranges[i]
    // Durations are added as elapsed time, preserving meal length across DST.
    slots.push({
      index: r.index,
      day: dayStart,
      start: new Date(windowStart.getTime() + r.start_minutes * 60000),
      end: new Date(windowStart.getTime() + r.end_minutes * 60000),
      duration_minutes: r.duration_minutes
    })
  }
  return slots
}

function sameInstant(a, b) {
  return a.getTime() === b.getTime()
}

/** Computes the phase and next events at now. Throws on an invalid plan. */
function compute(p, now) {
  var invalid = validate(p)
  if (invalid) throw new Error(invalid)

  var day = dayStartOf(now)
  var slots = []
  var offsets = [-1, 0, 1]
  for (var d = 0; d < offsets.length; d++) {
    var cycleDay = new Date(
      day.getFullYear(),
      day.getMonth(),
      day.getDate() + offsets[d],
      0, 0, 0, 0
    )
    slots = slots.concat(slotsForDate(p, cycleDay))
  }
  slots.sort(function (a, b) {
    return a.start.getTime() - b.start.getTime()
  })
  if (!slots.length) throw new Error('No meals configured')

  var runStart = []
  var runEnd = []
  var i
  for (i = 0; i < slots.length; i++) {
    if (i > 0 && sameInstant(slots[i].start, slots[i - 1].end)) {
      runStart[i] = runStart[i - 1]
    } else {
      runStart[i] = i
    }
  }
  for (i = slots.length - 1; i >= 0; i--) {
    if (i < slots.length - 1 && sameInstant(slots[i].end, slots[i + 1].start)) {
      runEnd[i] = runEnd[i + 1]
    } else {
      runEnd[i] = i
    }
  }

  var nowMs = now.getTime()
  var current = -1
  for (i = 0; i < slots.length; i++) {
    if (nowMs >= slots[i].start.getTime() && nowMs < slots[i].end.getTime()) {
      current = i
      break
    }
  }

  var state = {
    now: now,
    phase: 'fasting',
    phaseStart: null,
    phaseEnd: null,
    currentMeal: null,
    nextMeal: null,
    nextEatingPhaseStart: null,
    nextEatingPhaseMeal: 0,
    nextWindowStart: null,
    firstMealIndex: 0,
    lastMealIndex: 0,
    mealCount: p.meals,
    fastingMinutes: fastingMinutes(p),
    longFastMinutes: Math.max(0, MINUTES_PER_DAY - spanMinutes(p)),
    remainingMs: 0,
    progress: 0
  }

  if (current >= 0) {
    var first = runStart[current]
    var last = runEnd[current]
    var meal = slots[current]
    state.phase = 'eating'
    state.currentMeal = meal
    state.phaseStart = slots[first].start
    state.phaseEnd = slots[last].end
    state.firstMealIndex = slots[first].index
    state.lastMealIndex = slots[last].index
    if (current + 1 < slots.length) state.nextMeal = slots[current + 1]
    if (last + 1 < slots.length) {
      state.nextEatingPhaseStart = slots[last + 1].start
      state.nextEatingPhaseMeal = slots[last + 1].index
    }
  } else {
    var next = -1
    for (i = 0; i < slots.length; i++) {
      if (slots[i].start.getTime() > nowMs) {
        next = i
        break
      }
    }
    if (next < 0) throw new Error('No upcoming meal')

    state.phase = 'fasting'
    state.nextMeal = slots[next]
    state.phaseEnd = slots[next].start
    state.firstMealIndex = slots[next].index
    state.lastMealIndex = slots[runEnd[next]].index
    state.nextEatingPhaseStart = slots[next].start
    state.nextEatingPhaseMeal = slots[next].index

    var previousEnd = null
    for (i = 0; i < slots.length; i++) {
      if (slots[i].end.getTime() <= nowMs) previousEnd = slots[i].end
    }
    state.phaseStart = previousEnd || state.phaseEnd
  }

  for (i = 0; i < slots.length; i++) {
    if (slots[i].index === 1 && slots[i].start.getTime() > nowMs) {
      state.nextWindowStart = slots[i].start
      break
    }
  }

  state.remainingMs = Math.max(0, state.phaseEnd.getTime() - nowMs)
  var total = state.phaseEnd.getTime() - state.phaseStart.getTime()
  if (total > 0) {
    state.progress = Math.max(0, Math.min(1, (nowMs - state.phaseStart.getTime()) / total))
  }
  return state
}

module.exports = {
  MINUTES_PER_DAY: MINUTES_PER_DAY,
  MAX_MEALS: MAX_MEALS,
  MIN_MEAL_MINUTE: MIN_MEAL_MINUTE,
  defaultPlan: defaultPlan,
  copyPlan: copyPlan,
  parse: parse,
  validate: validate,
  fastingMinutes: fastingMinutes,
  spanMinutes: spanMinutes,
  mealRanges: mealRanges,
  slotsForDate: slotsForDate,
  compute: compute
}
