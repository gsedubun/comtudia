package com.comtudia.app.core

/** A platform-independent notification payload. */
data class Message(val title: String, val body: String)

/**
 * Emits one message per phase change. The first [update] primes the notifier so
 * launching the app does not fire a notification.
 */
class Notifier {
    private val lock = Any()
    private var key: String? = null
    private var primed = false

    /** Primes the notifier again, discarding the previous phase. */
    fun reset() {
        synchronized(lock) {
            key = null
            primed = false
        }
    }

    /** Records the state and returns a message when the phase changed. */
    fun update(state: ScheduleState): Message? = synchronized(lock) {
        val nextKey = state.phaseKey()
        when {
            !primed -> {
                primed = true
                key = nextKey
                null
            }
            nextKey == key -> null
            else -> {
                key = nextKey
                phaseMessage(state)
            }
        }
    }
}

/**
 * Builds the phase-change message for a computed state.
 *
 * Shared with the Android alarm receiver, which runs in a fresh process where no
 * [Notifier] state exists. Text matches `internal/notify/notify.go`.
 */
fun phaseMessage(state: ScheduleState): Message {
    if (state.phase == Phase.EATING) {
        if (state.firstMealIndex != state.lastMealIndex) {
            return Message(
                title = "Eating window open",
                body = "Meals ${state.firstMealIndex}\u2013${state.lastMealIndex} " +
                    "until ${Format.clock(state.phaseEnd)}",
            )
        }
        return Message(
            title = "Eating time",
            body = "Meal ${state.firstMealIndex} of ${state.mealCount} " +
                "until ${Format.clock(state.phaseEnd)}",
        )
    }

    val next = state.nextEatingPhaseStart?.let { Format.whenPhrase(it, state.now) } ?: "soon"
    return Message(
        title = "Fasting started",
        body = "Next eating $next (in ${Format.duration(state.remaining)})",
    )
}
