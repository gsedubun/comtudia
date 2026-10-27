package com.comtudia.app.sync

import android.content.Context
import android.util.Log
import com.comtudia.app.core.Plan
import com.comtudia.app.core.PlanJson
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Phone side of the Wearable Data Layer handshake.
 *
 * The phone owns the plan: every change is pushed as a single `/comtudia/plan`
 * data item, and the watch asks for it on launch with `/comtudia/request`.
 */
object WearSync {
    const val PATH_PLAN = "/comtudia/plan"
    const val PATH_REQUEST = "/comtudia/request"
    const val KEY_JSON = "json"

    private const val TAG = "WearSync"

    /** Pushes [plan] to every paired watch. */
    fun pushPlan(context: Context, plan: Plan) {
        val request = PutDataMapRequest.create(PATH_PLAN).apply {
            dataMap.putString(KEY_JSON, PlanJson.encode(plan))
            dataMap.putLong("updated_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnFailureListener { error ->
                Log.w(TAG, "plan push failed: ${error.message}")
            }
    }
}
