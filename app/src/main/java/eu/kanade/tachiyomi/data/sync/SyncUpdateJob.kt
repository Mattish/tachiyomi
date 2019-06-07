package eu.kanade.tachiyomi.data.sync

import android.util.Log
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Exception

class SyncUpdateJob : Job() {

    override fun onRunJob(params: Params): Result {
        try {
            SyncUpdateService.start(context)
            Log.i(TAG, "Job Started");
            return Job.Result.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Job Failed $e");
            return Job.Result.FAILURE
        }
    }

    companion object {
        const val TAG = "SyncUpdate"

        fun setupTask() {
            val preferences = Injekt.get<PreferencesHelper>()

            if (SyncSettingsAccess.isRegistered() && SyncSettingsAccess.getSettings().automaticEnabled) {
                JobManager.instance().cancelAllForTag(TAG)
                val restrictions = preferences.libraryUpdateRestriction()
                val acRestriction = "ac" in restrictions
                val wifiRestriction = if ("wifi" in restrictions)
                    JobRequest.NetworkType.UNMETERED
                else
                    JobRequest.NetworkType.CONNECTED
                val intervalInMs = SyncSettingsAccess.getSettings().automaticMinutesInterval * 60 * 1000L;
                JobRequest.Builder(TAG)
                        .setPeriodic(intervalInMs, 6 * 60 * 1000)
                        .setRequiredNetworkType(wifiRestriction)
                        .setRequiresCharging(acRestriction)
                        .setRequirementsEnforced(true)
                        .setUpdateCurrent(true)
                        .build()
                        .schedule()
                Log.i(TAG, "JobScheduled");
            }
        }

        fun cancelTask() {
            JobManager.instance().cancelAllForTag(TAG)
            Log.i(TAG, "JobCancelled");
        }
    }
}