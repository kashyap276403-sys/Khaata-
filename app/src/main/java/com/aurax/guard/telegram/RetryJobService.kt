package com.aurax.guard.telegram

import android.app.job.JobParameters
import android.app.job.JobService

/** Android wakes this when the internet is back, so queued alerts go out without opening the app. */
class RetryJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                TelegramDispatcher.runOnce(applicationContext, false)
            } catch (_: Exception) {
            } finally {
                val more = try { TelegramDispatcher.hasWaiting(applicationContext) } catch (_: Exception) { false }
                jobFinished(params, more)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
