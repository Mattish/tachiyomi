package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceScreen
import android.support.v7.preference.SwitchPreferenceCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.sync.SyncSettingsAccess
import eu.kanade.tachiyomi.data.sync.SyncStateAccess
import eu.kanade.tachiyomi.data.sync.SyncUpdateJob
import eu.kanade.tachiyomi.data.sync.SyncUpdateService
import eu.kanade.tachiyomi.data.sync.SyncUpdateService.Companion.TAG
import eu.kanade.tachiyomi.data.sync.model.SyncSettingsDto
import eu.kanade.tachiyomi.widget.preference.SyncDialogPreference
import rx.Completable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys


class SettingsSyncController : SettingsController(), SyncSettingsAccess.ISyncSettingsAccessListener {

    private var switchPref: SwitchPreferenceCompat? = null;
    private var registrationStatusPref: Preference? = null;
    private var manualSyncPref: Preference? = null;
    private var currentSyncPref: Preference? = null;
    private var lastCheckedRemoteSyncPref: Preference? = null;

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        SyncSettingsAccess.addListener(this)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_sync

        switchPref = switchPreference {
            titleRes = R.string.pref_enable_sync
            defaultValue = SyncSettingsAccess.isRegistered() && SyncSettingsAccess.getSettings().automaticEnabled
            isEnabled = SyncSettingsAccess.isRegistered()
            onChange { newValue ->
                val checked = newValue as Boolean
                SyncSettingsAccess.updateAutomaticEnabledSetting(checked)

                if (checked) {
                    SyncUpdateJob.setupTask();
                } else {
                    SyncUpdateJob.cancelTask()
                }
                true
            }
        }
        registrationStatusPref = preference {
            titleRes = R.string.pref_sync_registration_configuration
            onClick {
                val dialog = SyncDialogPreference()
                dialog.showDialog(router)
            }
        }
        manualSyncPref = preference {
            title = "Manual Sync"
            summary = "Perform manual sync"
            isEnabled = SyncSettingsAccess.isRegistered()
            onClick {
                SyncUpdateService.start(context);
            }
        }
        currentSyncPref = preference {
            title = "Current Sync Version"
            summary = if (SyncStateAccess.doesSyncFileExist()) "${SyncStateAccess.getState().versionNumber} - UUID:${SyncStateAccess.getState().guid}" else ""
        }
        lastCheckedRemoteSyncPref = preference {
            title = "Last Checked Remote"
            summary = if (SyncSettingsAccess.isRegistered()) "${SyncSettingsAccess.getSettings().lastChecked}" else ""
        }
    }

    override fun onSettingsChanged(newSettings: SyncSettingsDto) {
        Completable.complete()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    updatePreferenceSummaries()
                }

    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
    }

    private fun updatePreferenceSummaries() {
        view?.apply {
            switchPref?.isEnabled = SyncSettingsAccess.isRegistered()
            switchPref?.isChecked = SyncSettingsAccess.isRegistered() && SyncSettingsAccess.getSettings().automaticEnabled
            registrationStatusPref?.summary = if (SyncSettingsAccess.isRegistered()) "" else "Not configured"
            manualSyncPref?.isEnabled = SyncSettingsAccess.isRegistered()
            if (SyncSettingsAccess.isRegistered()) {
                SyncUpdateJob.setupTask();
            } else {
                SyncUpdateJob.cancelTask()
            }
            currentSyncPref?.summary = if (SyncStateAccess.doesSyncFileExist()) "${SyncStateAccess.getState().versionNumber} - UUID:${SyncStateAccess.getState().guid}" else ""
            lastCheckedRemoteSyncPref?.summary = if (SyncSettingsAccess.isRegistered()) "${SyncSettingsAccess.getSettings().lastChecked}" else ""
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        SyncSettingsAccess.removeListener(this)
    }
}
