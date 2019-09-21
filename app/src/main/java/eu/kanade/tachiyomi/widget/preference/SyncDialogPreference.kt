package eu.kanade.tachiyomi.widget.preference

import android.app.Dialog
import android.os.Bundle
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.dd.processbutton.iml.ActionProcessButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.sync.SyncApiAccess
import eu.kanade.tachiyomi.data.sync.SyncSettingsAccess
import eu.kanade.tachiyomi.data.sync.SyncStateAccess
import eu.kanade.tachiyomi.data.sync.model.AggregateSyncState
import eu.kanade.tachiyomi.data.sync.model.ChapterResponseDto
import eu.kanade.tachiyomi.data.sync.model.MangaResponseDto
import eu.kanade.tachiyomi.data.sync.model.StateResponseDto
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.toast
import kotlinx.android.synthetic.main.sync_register_dialog.view.*
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*
import android.text.Editable
import android.text.TextWatcher
import eu.kanade.tachiyomi.data.sync.SyncUpdateService


class SyncDialogPreference(val db: DatabaseHelper = Injekt.get(), bundle: Bundle? = null) : DialogController(bundle) {

    enum class State {
        UNREGISTERED, REGISTERED, REGISTERING_NEW, REGISTERING_ACCOUNT_CODE, REGISTERING_RECOVERY, REVOKE_FIRST
    }

    var v: View? = null
        private set

    private var newRegisterSubscription: Subscription? = null
    private var generateAccountCodeSubscription: Subscription? = null

    override fun onCreateDialog(savedState: Bundle?): Dialog {
        val dialog = MaterialDialog.Builder(activity!!)
                .customView(R.layout.sync_register_dialog, false)
                .negativeText(R.string.pref_sync_registration_close)
                .build()

        onViewCreated(dialog.view)

        return dialog
    }

    fun onViewCreated(view: View) {
        v = view.apply {
            sync_register.setMode(ActionProcessButton.Mode.ENDLESS)
            sync_register.setOnClickListener { doNewRegister() }
            sync_revoke.setOnClickListener { doFirstRevoke() }
            sync_revoke_confirm.setOnClickListener { doConfirmRevoke() }
            sync_register_generate_account_code.setOnClickListener { doGenerateCode() }
            sync_register_recovery_code.setOnClickListener { doRecoveryRegister() }
            sync_register_account_code.setOnClickListener { doAccountCodeRegister() }
            sync_server_endpoint.setText(SyncSettingsAccess.getSettings().endpoint)
            sync_server_endpoint.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun afterTextChanged(p0: Editable?) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int , count: Int) {
                    SyncSettingsAccess.setEndpoint(s.toString())
                }
            })
        }
        if (SyncSettingsAccess.isRegistered()) {
            updateViewTo(State.REGISTERED)
        } else {
            updateViewTo(State.UNREGISTERED)
        }

    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isEnter) {
            onDialogClosed()
        }
    }

    fun onDialogClosed() {
        newRegisterSubscription?.unsubscribe()
        generateAccountCodeSubscription?.unsubscribe()
        (targetController as? Listener)?.syncDialogClosed()
    }

    private fun doFirstRevoke() {
        updateViewTo(State.REVOKE_FIRST)
    }

    private fun doConfirmRevoke() {
        SyncSettingsAccess.deleteSyncSettings();
        SyncStateAccess.deleteState();
        updateViewTo(State.UNREGISTERED)
    }

    private fun doAccountCodeRegister() {
        v?.apply {
            updateViewTo(State.REGISTERING_ACCOUNT_CODE)
            newRegisterSubscription = Single.just("Get")
                    .map {
                        val accountCode = sync_text_input.text.toString()
                        val registrationResponseDto = SyncApiAccess.sendAccountCodeRegistration(UUID.randomUUID(), accountCode)
                        val currentLibStateResponse = StateResponseDto(0, mutableListOf(), mutableListOf(), Date(System.currentTimeMillis()))
                        val newAggregateState = AggregateSyncState(-1, Date(System.currentTimeMillis()), mutableListOf())
                        newAggregateState.applyStateResponseDto(currentLibStateResponse)
                        SyncStateAccess.writeState(newAggregateState)
                        SyncSettingsAccess.setSettings(registrationResponseDto.recoveryCode, registrationResponseDto.secretToken)
                        SyncSettingsAccess.updateLastCheckedDate(Date(System.currentTimeMillis()))
                        SyncUpdateService.start(context)
                        registrationResponseDto
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        updateViewTo(State.REGISTERED)
                        activity?.toast(R.string.sync_register_success)
                    }, { error ->
                        sync_register_account_code.progress = -1
                        sync_register_account_code.isEnabled = true
                        sync_register_account_code.setText(R.string.sync_register_error)
                        error.message?.let { context.toast(it) }
                    })
        }
    }

    private fun doRecoveryRegister() {
        updateViewTo(State.REGISTERING_RECOVERY)
    }

    private fun doGenerateCode() {
        v?.apply {
            generateAccountCodeSubscription = Single.just("GetAccountCode")
                    .map {
                        val accountCodeResponse = SyncApiAccess.getAccountCode()
                        accountCodeResponse
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        sync_register_generate_account_code.text = it.code
                        sync_register_generate_account_code.isEnabled = false
                    }, { error ->
                        sync_register_generate_account_code.text = "Failed to get code"
                        sync_register_generate_account_code.progress = -1
                        error.message?.let { context.toast(it) }
                    })

        }
    }

    private fun doNewRegister() {
        v?.apply {
            updateViewTo(State.REGISTERING_NEW)
            newRegisterSubscription = Single.just("Get")
                    .map {
                        val currentLibrary = getCurrentLibrary()
                        val currentLibStateResponse = StateResponseDto(1, currentLibrary.toMutableList(), mutableListOf(), Date(System.currentTimeMillis()))
                        val registrationResponseDto = SyncApiAccess.sendRegistration(currentLibStateResponse)
                        val newAggregateState = AggregateSyncState(registrationResponseDto.initialState.versionNumber, registrationResponseDto.initialState.timestamp, mutableListOf())
                        newAggregateState.applyStateResponseDto(registrationResponseDto.initialState)
                        SyncStateAccess.writeState(newAggregateState)
                        SyncSettingsAccess.setSettings(registrationResponseDto.recoveryCode, registrationResponseDto.secretToken)
                        SyncSettingsAccess.updateLastCheckedDate(Date(System.currentTimeMillis()))
                        newAggregateState
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        updateViewTo(State.REGISTERED)
                        activity?.toast(R.string.sync_register_success)
                    }, { error ->
                        sync_register.progress = -1
                        sync_register.isEnabled = true
                        sync_register.setText(R.string.sync_register_error)
                        error.message?.let { context.toast(it) }
                    })
        }
    }

    private fun getCurrentLibrary(): List<MangaResponseDto> {
        return db.getLibraryMangas()
                .executeAsBlocking()
                .filter { it.id != null }
                .map {
                    MangaResponseDto(it.url, it.title, it.source, it.thumbnail_url,it.last_update,it.artist,it.author,
                            db.getChapters(it).executeAsBlocking().map {
                                ChapterResponseDto(it.url, it.name, it.date_upload, it.date_fetch, it.chapter_number, it.read, it.bookmark, it.last_page_read, it.source_order)
                            }.toMutableList())

                }
    }

    private fun updateViewTo(newState: SyncDialogPreference.State) {
        v?.apply {
            when (newState) {
                State.UNREGISTERED -> {
                    sync_server_endpoint.setText(SyncSettingsAccess.getSettings().endpoint)
                    sync_register.isEnabled = true
                    sync_register.setText(R.string.sync_register_new)
                    sync_register.progress = 0
                    sync_device_id.text = "-"
                    sync_recovery_code.text = "-"

                    sync_register.visibility = View.VISIBLE
                    sync_register_account_code.visibility = View.VISIBLE
                    sync_register_recovery_code.visibility = View.VISIBLE
                    sync_text_input.visibility = View.VISIBLE
                    sync_revoke.visibility = View.GONE
                    sync_revoke_confirm.visibility = View.GONE
                    sync_register_generate_account_code.visibility = View.GONE
                }
                State.REGISTERED -> {
                    sync_register.progress = 100
                    sync_register.setText(R.string.sync_register_success)
                    sync_register.isEnabled = true
                    val currentSyncSettings = SyncSettingsAccess.getSettings()
                    sync_server_endpoint.setText(currentSyncSettings.endpoint)
                    sync_device_id.text = currentSyncSettings.deviceId.toString()
                    sync_recovery_code.text = currentSyncSettings.recoveryCode.toString()

                    sync_register.visibility = View.GONE
                    sync_register_account_code.visibility = View.GONE
                    sync_register_recovery_code.visibility = View.GONE
                    sync_text_input.visibility = View.GONE
                    sync_revoke.visibility = View.VISIBLE
                    sync_revoke_confirm.visibility = View.GONE
                    sync_register_generate_account_code.visibility = View.VISIBLE
                }
                State.REGISTERING_NEW -> {
                    sync_register.progress = 1
                    sync_register.isEnabled = false

                    sync_register.visibility = View.VISIBLE
                    sync_register_account_code.visibility = View.GONE
                    sync_register_recovery_code.visibility = View.GONE
                    sync_text_input.visibility = View.GONE
                    sync_revoke.visibility = View.GONE
                    sync_revoke_confirm.visibility = View.GONE
                    sync_register_generate_account_code.visibility = View.GONE
                }
                State.REVOKE_FIRST -> {
                    sync_register.progress = 100
                    sync_register.setText(R.string.sync_register_success)
                    sync_register.isEnabled = true
                    val currentSyncSettings = SyncSettingsAccess.getSettings()
                    sync_server_endpoint.setText(currentSyncSettings.endpoint)
                    sync_device_id.text = currentSyncSettings.deviceId.toString()
                    sync_recovery_code.text = currentSyncSettings.recoveryCode.toString()

                    sync_register.visibility = View.GONE
                    sync_register_account_code.visibility = View.GONE
                    sync_register_recovery_code.visibility = View.GONE
                    sync_text_input.visibility = View.GONE
                    sync_revoke.visibility = View.GONE
                    sync_revoke_confirm.visibility = View.VISIBLE
                    sync_register_generate_account_code.visibility = View.GONE
                }
                State.REGISTERING_ACCOUNT_CODE -> {
                    sync_register_account_code.progress = 1
                    sync_register_account_code.isEnabled = false

                    sync_register_account_code.visibility = View.VISIBLE
                    sync_register.visibility = View.GONE
                    sync_register_recovery_code.visibility = View.GONE
                    sync_text_input.visibility = View.GONE
                    sync_revoke.visibility = View.GONE
                    sync_revoke_confirm.visibility = View.GONE
                    sync_register_generate_account_code.visibility = View.GONE
                }
                State.REGISTERING_RECOVERY -> {
                    sync_register_recovery_code.progress = 1
                    sync_register_recovery_code.isEnabled = false

                    sync_register_account_code.visibility = View.GONE
                    sync_register.visibility = View.GONE
                    sync_register_recovery_code.visibility = View.VISIBLE
                    sync_text_input.visibility = View.GONE
                    sync_revoke.visibility = View.GONE
                    sync_revoke_confirm.visibility = View.GONE
                    sync_register_generate_account_code.visibility = View.GONE
                }
            }
        }
    }

    interface Listener {
        fun syncDialogClosed()
    }

}
