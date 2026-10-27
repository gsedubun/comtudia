package com.comtudia.app.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives plan documents pushed by the phone. */
class WearSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != WearSync.PATH_PLAN) continue

                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = map.getString(WearSync.KEY_JSON) ?: continue
                WearPlanStore.get(this).saveJson(json)
            }
        } finally {
            dataEvents.release()
        }
    }
}
