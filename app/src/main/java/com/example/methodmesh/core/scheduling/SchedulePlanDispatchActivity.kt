package com.example.methodmesh.core.scheduling

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class SchedulePlanDispatchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val instance = SchedulePlanStore.instance(this, intent.getStringExtra("instance_id").orEmpty())
        val occurrence = instance?.occurrences?.firstOrNull { it.id == intent.getStringExtra("occurrence_id") }
        if (instance == null || occurrence == null) { finish(); return }
        Toast.makeText(this, "${occurrence.laneName} is due", Toast.LENGTH_LONG).show()
        SchedulePlanStore.updateOccurrence(this, instance.id, occurrence.id, ScheduleOccurrenceState.COMPLETED, java.time.ZonedDateTime.now())
        SchedulePlanRuntime.armNext(this, SchedulePlanStore.instance(this, instance.id) ?: instance)
        finish()
    }
}
