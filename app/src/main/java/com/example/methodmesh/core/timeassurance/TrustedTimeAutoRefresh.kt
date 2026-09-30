package com.example.methodmesh.core.timeassurance

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quiet background refresh for shared Clock Assurance evidence.
 *
 * Network availability is expressed as a JobScheduler constraint, so an
 * offline device simply waits until connectivity returns. No notification or
 * foreground service is used. Job timing is deliberately inexact: this keeps
 * the refresh battery-friendly while preserving fresh trusted-time anchors.
 */
object TrustedTimeAutoRefresh {
    const val PERIODIC_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
    const val FOREGROUND_FRESHNESS_MILLIS = 15L * 60L * 1000L

    private const val PERIODIC_JOB_ID = 0x4D4D5401
    private const val IMMEDIATE_JOB_ID = 0x4D4D5402

    fun schedulePeriodic(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return

        val job = JobInfo.Builder(
            PERIODIC_JOB_ID,
            ComponentName(context, TrustedTimeAutoRefreshJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(PERIODIC_INTERVAL_MILLIS)
            .build()
        scheduler.schedule(job)
    }

    /**
     * Request a refresh when the launcher activity becomes visible, unless a
     * recently acquired trusted anchor is already fresh enough. If the device
     * is offline the job remains pending until Android reports connectivity.
     */
    fun requestOnAppOpen(context: Context) {
        val anchorAge = runCatching { ClockAssuranceRuntime.snapshot().anchorAgeMillis }.getOrNull()
        if (anchorAge != null && anchorAge <= FOREGROUND_FRESHNESS_MILLIS) return

        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(
            IMMEDIATE_JOB_ID,
            ComponentName(context, TrustedTimeAutoRefreshJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(15L * 60L * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }

    internal fun isPeriodic(jobId: Int): Boolean = jobId == PERIODIC_JOB_ID
}

class TrustedTimeAutoRefreshJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = serviceScope.launch {
            val result = runCatching { TrustedTimeRefreshRegistry.refresh() }.getOrNull()
            val retry = !TrustedTimeAutoRefresh.isPeriodic(params.jobId) && result?.success != true
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        // One-shot app-open work may retry; periodic work gets another normal interval.
        return !TrustedTimeAutoRefresh.isPeriodic(params.jobId)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
