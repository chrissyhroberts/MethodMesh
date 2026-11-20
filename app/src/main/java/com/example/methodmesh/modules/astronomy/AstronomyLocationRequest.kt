package com.example.methodmesh.modules.astronomy

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** GPS belongs to the requesting composition, including its cancellation. */
internal suspend fun astronomyDeviceLocation(context: Context): AstroLocation {
    check(hasLocationPermission(context)) { "Location permission is required for automatic GPS mode." }
    val location = awaitAstronomyLocation { token ->
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token)
    } ?: error("Current GPS position is unavailable.")
    return AstroLocation(location.latitude, location.longitude, "live GPS")
}

internal suspend fun awaitAstronomyLocation(start: (CancellationToken) -> Task<Location>): Location? =
    suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            start(cancellation.token)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                .addOnCanceledListener { continuation.cancel() }
        } catch (failure: Exception) {
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
    }
