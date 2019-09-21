package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceScreen
import android.support.v7.preference.SwitchPreferenceCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.sync.*
import eu.kanade.tachiyomi.data.sync.model.SyncSettingsDto
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.registerLocalReceiver
import eu.kanade.tachiyomi.util.unregisterLocalReceiver
import eu.kanade.tachiyomi.widget.preference.SyncDialogPreference
import rx.Completable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys


class SettingsSyncController : SettingsController(), SyncSettingsAccess.ISyncSettingsAccessListener {

    private val TAG_MANUAL_SYNC_DIALOG = "ManualSyncDialog"

    private var switchPref: SwitchPreferenceCompat? = null;
    private var registrationStatusPref: Preference? = null;
    private var manualSyncPref: Preference? = null;
    private var currentSyncPref: Preference? = null;
    private var lastCheckedRemoteSyncPref: Preference? = null;

    private val receiver = SyncBroadcastReceiver()

    init {
        preferences.context.registerLocalReceiver(receiver, IntentFilter(SyncConst.INTENT_FILTER))
    }

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
                ManualSyncDialog().showDialog(router,TAG_MANUAL_SYNC_DIALOG)
                SyncUpdateService.start(context)
            }
        }
        currentSyncPref = preference {
            title = "Current Sync Version"
            summary = if (SyncStateAccess.doesSyncFileExist()) "${SyncStateAccess.getState().versionNumber} - Timestamp:${SyncStateAccess.getState().timestamp}" else ""
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
            currentSyncPref?.summary = if (SyncStateAccess.doesSyncFileExist()) "${SyncStateAccess.getState().versionNumber} - Timestamp:${SyncStateAccess.getState().timestamp}" else ""
            lastCheckedRemoteSyncPref?.summary = if (SyncSettingsAccess.isRegistered()) "${SyncSettingsAccess.getSettings().lastChecked}" else ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.context.unregisterLocalReceiver(receiver)
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        SyncSettingsAccess.removeListener(this)
    }

    class ManualSyncDialog : DialogController() {
        private var materialDialog: MaterialDialog? = null

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog.Builder(activity!!)
                    .title(R.string.manualSync)
                    .content(R.string.performing_sync)
                    .progress(false, 100, false)
                    .cancelable(false)
                    .negativeText(R.string.action_stop)
                    .onNegative { _, _ ->
                        applicationContext?.let {  }
                    }
                    .build()
                    .also { materialDialog = it }
        }

        override fun onDestroyView(view: View) {
            super.onDestroyView(view)
            materialDialog = null
        }

        override fun onRestoreInstanceState(savedInstanceState: Bundle) {
            super.onRestoreInstanceState(savedInstanceState)
            router.popController(this)
        }

        fun updateProgress(content: String?, progress: Int) {
            val dialog = materialDialog ?: return
            dialog.setContent(content)
            dialog.setProgress(progress)
        }
    }

    inner class SyncBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val progress = intent.getIntExtra(SyncConst.EXTRA_PROGRESS, 0)
            val content = intent.getStringExtra(SyncConst.EXTRA_CONTENT)
            (router.getControllerWithTag(TAG_MANUAL_SYNC_DIALOG)
                    as? ManualSyncDialog)?.updateProgress(content, progress)
        }
    }
}
