package eu.kanade.tachiyomi.data.sync

import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.sync.SyncUpdateService.Companion.TAG
import eu.kanade.tachiyomi.data.sync.model.SyncSettingsDto
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SyncSettingsAccess {

    private constructor()

    interface ISyncSettingsAccessListener {
        fun onSettingsChanged(newSettings: SyncSettingsDto);
    }

    companion object {

        private val appName = Injekt.get<PreferencesHelper>().context.getString(R.string.app_name)
        private val gson: Gson = Injekt.get()

        private val settingsFile = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + appName, "sync/sync_settings.json")

        private val fileLock: Lock = ReentrantLock()
        private val listenersLock: Lock = ReentrantLock()
        private val listeners: MutableList<ISyncSettingsAccessListener> = mutableListOf()

        private val defaultEndpoint = "http://192.168.0.12:34743"

        fun addListener(listener: ISyncSettingsAccessListener) {
            listenersLock.withLock {
                listeners.add(listener)
            }
        }

        fun removeListener(listener: ISyncSettingsAccessListener) {
            listenersLock.withLock {
                listeners.remove(listener)
            }
        }

        private fun callOnSettingsChanged(newSettings: SyncSettingsDto) {
            var listenersToCall = listOf<ISyncSettingsAccessListener>()
            listenersLock.withLock {
                listenersToCall = listeners.toList()
            }

            for (listener in listenersToCall) {
                try {
                    listener.onSettingsChanged(newSettings)
                } catch (e: Exception) {

                }
            }
        }

        fun isRegistered(): Boolean {
            return !getSettings().accessToken.isNullOrBlank()
        }

        fun getSettings(): SyncSettingsDto {
            fileLock.withLock {
                ensureSettingsFileExists()
                val json = settingsFile.readText()
                return gson.fromJson(json, SyncSettingsDto::class.java)
            }
        }

        fun deleteSyncSettings() {
            fileLock.withLock {
                ensureSettingsFileExists()
                val currentSettings = getSettings();
                val settingsDto = SyncSettingsDto(currentSettings.endpoint, currentSettings.deviceId, null, null, currentSettings.automaticEnabled, currentSettings.automaticMinutesInterval, null)
                val json = gson.toJson(settingsDto)
                settingsFile.writeText(json)
                callOnSettingsChanged(settingsDto)
            }
        }

        fun updateLastCheckedDate(dateTime: Date) {
            fileLock.withLock {
                ensureSettingsFileExists()
                val currentSettings = getSettings();
                val settingsDto = SyncSettingsDto(currentSettings.endpoint, currentSettings.deviceId, currentSettings.recoveryCode, currentSettings.accessToken, currentSettings.automaticEnabled, currentSettings.automaticMinutesInterval, dateTime)
                val json = gson.toJson(settingsDto)
                settingsFile.writeText(json)
                callOnSettingsChanged(settingsDto)
            }
        }

        fun updateAutomaticEnabledSetting(automaticEnabled: Boolean) {
            fileLock.withLock {
                ensureSettingsFileExists()
                val currentSettings = getSettings()
                val settingsDto = SyncSettingsDto(currentSettings.endpoint, currentSettings.deviceId, currentSettings.recoveryCode, currentSettings.accessToken, automaticEnabled, currentSettings.automaticMinutesInterval, currentSettings.lastChecked)
                val json = gson.toJson(settingsDto)
                settingsFile.writeText(json)
                callOnSettingsChanged(settingsDto)
            }
        }

        fun setSettings(recoveryCode: UUID, accessToken: String) {
            fileLock.withLock {
                ensureSettingsFileExists()
                val currentSettings = getSettings()
                val settingsDto = SyncSettingsDto(currentSettings.endpoint, currentSettings.deviceId, recoveryCode, accessToken, currentSettings.automaticEnabled, currentSettings.automaticMinutesInterval, currentSettings.lastChecked)
                val json = gson.toJson(settingsDto)
                settingsFile.writeText(json)
                callOnSettingsChanged(settingsDto)
            }
        }

        fun setEndpoint(endpoint: String) {
            fileLock.withLock {
                ensureSettingsFileExists()
                val currentSettings = getSettings()
                val settingsDto = SyncSettingsDto(endpoint, currentSettings.deviceId, currentSettings.recoveryCode, currentSettings.accessToken, currentSettings.automaticEnabled, currentSettings.automaticMinutesInterval, currentSettings.lastChecked)
                val json = gson.toJson(settingsDto)
                settingsFile.writeText(json)
                callOnSettingsChanged(settingsDto)
            }
        }

        private fun ensureSettingsFileExists() {
            fileLock.withLock {
                if (!settingsFile.exists()) {
                    var parentFolder = settingsFile.parentFile
                    while(!parentFolder.exists()) {
                        val createdParentFolder = parentFolder.mkdirs()
                        Log.w(TAG,"Creating folder '$parentFolder' for sync files. Success:$createdParentFolder")
                        parentFolder = parentFolder.parentFile
                    }
                    settingsFile.createNewFile()
                    val settingsDto = SyncSettingsDto(defaultEndpoint, UUID.randomUUID(), null, null, true, 15, null)
                    val json = gson.toJson(settingsDto)
                    settingsFile.writeText(json)
                }
            }
        }
    }


}