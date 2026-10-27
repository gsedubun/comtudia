/** Small, dependency-free formatting helpers for the band UI. */

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

function minutesLabel(minutes) {
  if (minutes <= 0) return '0m'
  var h = Math.floor(minutes / 60)
  var m = minutes % 60
  if (h === 0) return m + 'm'
  if (m === 0) return h + 'h'
  return h + 'h ' + pad2(m) + 'm'
}

/** Compact stepper value: "6h", "6h30", "45m". */
function compactMinutes(minutes) {
  if (minutes <= 0) return '0m'
  var h = Math.floor(minutes / 60)
  var m = minutes % 60
  if (h === 0) return m + 'm'
  if (m === 0) return h + 'h'
  return h + 'h' + pad2(m)
}

function duration(ms) {
  if (ms <= 0) return '0m'
  var total = Math.round(ms / 60000)
  if (total < 1) return '1m'
  var h = Math.floor(total / 60)
  var m = total % 60
  if (h === 0) return m + 'm'
  if (m === 0) return h + 'h'
  return h + 'h ' + pad2(m) + 'm'
}

/** Exact desktop-style countdown HH:MM:SS, truncating partial seconds. */
function countdown(ms) {
  if (ms <= 0) return '00:00:00'
  var total = Math.floor(ms / 1000)
  var h = Math.floor(total / 3600)
  var m = Math.floor((total % 3600) / 60)
  var s = total % 60
  return pad2(h) + ':' + pad2(m) + ':' + pad2(s)
}

/**
 * Wearable countdown: H:MM above one hour (or MM:SS below one hour).
 * Full HH:MM:SS is too wide for a 168 design-width capsule.
 */
function countdownShort(ms) {
  if (ms <= 0) return '0:00'
  var total = Math.floor(ms / 1000)
  if (total >= 3600) {
    var h = Math.floor(total / 3600)
    var m = Math.floor((total % 3600) / 60)
    return h + ':' + pad2(m)
  }
  var mm = Math.floor(total / 60)
  var ss = total % 60
  return pad2(mm) + ':' + pad2(ss)
}

function clock(date) {
  return pad2(date.getHours()) + ':' + pad2(date.getMinutes())
}

function clockMinutes(minutes) {
  var m = ((minutes % 1440) + 1440) % 1440
  return pad2(Math.floor(m / 60)) + ':' + pad2(m % 60)
}

function sameDate(a, b) {
  return a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
}

/** "12:00-14:00"; use spansDays() to note a cross-midnight end. */
function range(start, end) {
  return clock(start) + '-' + clock(end)
}

function spansDays(start, end) {
  return !sameDate(start, end)
}

function dayName(date, now) {
  if (sameDate(date, now)) return 'Today'
  var tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  var yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (sameDate(date, tomorrow)) return 'Tomorrow'
  if (sameDate(date, yesterday)) return 'Yesterday'
  return 'Day'
}

/** Ratio such as 18:6 for an 18h fast / 6h eat plan. */
function ratio(p) {
  var fast = Math.floor((1440 - p.eating_minutes) / 60)
  var eat = p.eating_minutes / 60
  if (eat % 1 !== 0) {
    return compactMinutes(1440 - p.eating_minutes) + '/' + compactMinutes(p.eating_minutes)
  }
  return fast + ':' + eat
}

module.exports = {
  pad2: pad2,
  minutesLabel: minutesLabel,
  compactMinutes: compactMinutes,
  duration: duration,
  countdown: countdown,
  countdownShort: countdownShort,
  clock: clock,
  clockMinutes: clockMinutes,
  range: range,
  spansDays: spansDays,
  sameDate: sameDate,
  dayName: dayName,
  ratio: ratio
}
