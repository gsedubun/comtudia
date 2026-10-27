package com.comtudia.app.sync

import com.comtudia.app.data.PlanRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Answers the watch when it opens and asks for the current plan.
 *
 * Runs in a fresh process, so the plan is read from disk rather than from the
 * activity's in-memory state.
 */
class WearSyncService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSync.PATH_REQUEST) return
        WearSync.pushPlan(this, PlanRepository(this).current())
    }
}
