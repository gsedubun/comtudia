package com.comtudia.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable

/**
 * Watch side of the Wearable Data Layer handshake.
 *
 * The phone is the source of truth: it pushes `/comtudia/plan` whenever the plan
 * changes, and the watch asks again with `/comtudia/request` each time it opens.
 */
object WearSync {
    const val PATH_PLAN = "/comtudia/plan"
    const val PATH_REQUEST = "/comtudia/request"
    const val KEY_JSON = "json"

    private const val TAG = "WearSync"

    /** Asks every connected phone node to push the current plan. */
    fun requestPlan(context: Context) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val messages = Wearable.getMessageClient(context)
                nodes.forEach { node ->
                    messages.sendMessage(node.id, PATH_REQUEST, ByteArray(0))
                        .addOnFailureListener { error ->
                            Log.w(TAG, "request to ${node.displayName} failed: ${error.message}")
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "no connected nodes: ${error.message}")
            }
    }
}
