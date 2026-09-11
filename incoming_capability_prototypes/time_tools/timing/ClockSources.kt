package com.example.methodmesh.modules.time_tools.timing

import java.time.Clock
import java.time.Instant

interface MonotonicClock {
    fun nowMs(): Long
}

/**
 * Monotonic elapsed-time source without an Android framework dependency.
 * System.nanoTime() is monotonic and suitable for measuring intervals; it is deliberately
 * unrelated to civil/device wall-clock changes.
 */
object AndroidMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = System.nanoTime() / 1_000_000L
}

class WallClock(private val clock: Clock = Clock.systemUTC()) {
    fun now(): Instant = clock.instant()
}
