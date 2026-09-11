package com.example.methodmesh.modules.time_tools.elapsed

import com.example.methodmesh.modules.time_tools.timing.TimeResultPayloads
import java.time.Instant

object ElapsedTimeCapability {
    fun execute(startTimestamp: String, endTimestamp: String): Map<String, Any?> {
        val start = Instant.parse(startTimestamp)
        val end = Instant.parse(endTimestamp)
        require(!end.isBefore(start)) { "End timestamp must not precede start timestamp" }
        return TimeResultPayloads.elapsed(start, end)
    }
}
