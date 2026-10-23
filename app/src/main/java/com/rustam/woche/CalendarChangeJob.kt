package com.rustam.woche

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Läuft, sobald sich im Kalender etwas ändert, und aktualisiert das Widget sofort. */
class CalendarChangeJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        CoroutineScope(Dispatchers.Default).launch {
            try { TodayWidget().updateAll(applicationContext) } catch (_: Exception) {}
            schedule(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val ID = 4711
        fun schedule(c: Context) {
            if (!CalendarRepo.hasPermission(c)) return
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(ID, ComponentName(c, CalendarChangeJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2_000)
                .setTriggerContentMaxDelay(30_000)
                .build()
            try { js.schedule(job) } catch (_: Exception) {}
        }
    }
}
