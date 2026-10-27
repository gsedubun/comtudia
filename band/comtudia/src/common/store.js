/** Persists the plan with @system.storage (Vela interface). */
var storage = require('@system.storage')
var plan = require('./plan')

var KEY = 'comtudia.plan.v1'

function load(success) {
  storage.get({
    key: KEY,
    default: '',
    success: function (value) {
      success(plan.parse(value))
    },
    fail: function () {
      success(plan.defaultPlan())
    }
  })
}

function save(value, success, fail) {
  storage.set({
    key: KEY,
    value: JSON.stringify(value),
    success: function () {
      if (success) success()
    },
    fail: function (data, code) {
      if (fail) fail(code)
    }
  })
}

module.exports = {
  KEY: KEY,
  load: load,
  save: save
}
