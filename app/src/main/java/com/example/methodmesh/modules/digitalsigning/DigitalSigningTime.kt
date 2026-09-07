package com.example.methodmesh.modules.digitalsigning

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DigitalSigningTime {
    private val fileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)
    fun nowIso(): String = Instant.now().toString()
    fun fileStamp(): String = fileFormatter.format(Instant.now())
}
