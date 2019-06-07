package eu.kanade.tachiyomi.data.sync

import android.os.Environment
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.sync.model.AggregateSyncState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SyncStateAccess{
    public interface ISyncStateAccessListener {
        fun onStateChanged(newSettings: AggregateSyncState);
    }

    companion object {

        private val appName = Injekt.get<PreferencesHelper>().context.getString(R.string.app_name)
        private val gson: Gson = Injekt.get()

        private val syncFile = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + appName, "sync/sync_state.json")
        private val fileLock: Lock = ReentrantLock()
        private val listenersLock: Lock = ReentrantLock()
        private val listeners: MutableList<ISyncStateAccessListener> = mutableListOf()

        private var currentState: AggregateSyncState? = null

        fun addListener(listener: ISyncStateAccessListener) {
            listenersLock.withLock {
                listeners.add(listener)
            }
        }

        fun removeListener(listener: ISyncStateAccessListener) {
            listenersLock.withLock {
                listeners.remove(listener)
            }
        }

        private fun callOnStateChanged(newSettings: AggregateSyncState) {
            var listenersToCall = listOf<ISyncStateAccessListener>()
            listenersLock.withLock {
                listenersToCall = listeners.toList()
            }

            for (listener in listenersToCall) {
                try {
                    listener.onStateChanged(newSettings)
                } catch (e: Exception) {

                }
            }
        }

        fun writeState(aggregateSyncState: AggregateSyncState) {
            fileLock.withLock {
                if (!syncFile.exists()) {
                    syncFile.parentFile.mkdirs() //Ensure dir path exists
                }
                syncFile.createNewFile()

                val json = gson.toJson(aggregateSyncState)
                syncFile.writeText(json)
                currentState = aggregateSyncState
            }
        }

        fun deleteState() {
            fileLock.withLock {
                if (syncFile.exists()) {
                    syncFile.delete()
                }
            }
        }

        fun doesSyncFileExist(): Boolean {
            return syncFile.exists()
        }

        fun getState(): AggregateSyncState {
            fileLock.withLock {
                if (currentState == null) {
                    if(doesSyncFileExist()){
                        val json = syncFile.readText()
                        currentState = gson.fromJson(json,AggregateSyncState::class.java)
                    }
                    else {
                        throw FileNotFoundException("Deletion of SyncSettingsFile not found at '${syncFile.absolutePath}'")
                    }
                }
                return currentState!!
            }
        }
    }

}