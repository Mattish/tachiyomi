package eu.kanade.tachiyomi.data.sync


import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.gson.Gson
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.isServiceRunning
import eu.kanade.tachiyomi.util.notification
import eu.kanade.tachiyomi.util.notificationManager
import rx.Subscription
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.*
import eu.kanade.tachiyomi.data.sync.model.*
import rx.Completable
import java.time.Instant
import java.util.*

class SyncUpdateService(
        val db: DatabaseHelper = Injekt.get(),
        val sourceManager: SourceManager = Injekt.get(),
        val preferences: PreferencesHelper = Injekt.get(),
        val gson: Gson = Injekt.get()
) : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private var subscription: Subscription? = null

    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(this)
    }

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
    }

    private val progressNotification by lazy {
        NotificationCompat.Builder(this, Notifications.CHANNEL_LIBRARY)
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_refresh_white_24dp_img)
                .setLargeIcon(notificationBitmap)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_clear_grey_24dp_img, getString(android.R.string.cancel), cancelIntent)
    }

    companion object {

        const val TAG = "SyncUpdate"

        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(SyncUpdateService::class.java)
        }

        fun start(context: Context) {
            Log.i(TAG, "ServiceAttemptStart")
            if (!isRunning(context)) {
                Log.i(TAG, "ServiceStarting")
                val intent = Intent(context, SyncUpdateService::class.java)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncUpdateService::class.java))
        }

    }

    override fun onCreate() {
        Log.i(TAG, "ServiceCreated")
        super.onCreate()
        startForeground(Notifications.ID_LIBRARY_PROGRESS, progressNotification.build())
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "SyncUpdateService:WakeLock")
        wakeLock.acquire()
    }

    override fun onDestroy() {
        subscription?.unsubscribe()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ServiceStarted")
        subscription?.unsubscribe()

        subscription = Completable
                .defer {
                    try {
                        Log.i(TAG, "DeferStarted")

                        val currentRemoteVersion = SyncApiAccess.getVersion()
                        val currentLocalState = SyncStateAccess.getState()


                        val requireRemoteSync = currentRemoteVersion != null && currentLocalState.versionNumber != currentRemoteVersion.versionNumber
                        if (requireRemoteSync) {
                            Log.i(TAG, "CurrentLocalVersion:${currentLocalState.versionNumber} CurrentRemoteVersion:${currentRemoteVersion?.versionNumber}")
                            val currentRemoteStates = SyncApiAccess.getCurrentStates(currentLocalState.versionNumber, currentLocalState.guid)

                            for (currentRemoteState in currentRemoteStates!!.states) {
                                val currentLibraryManga = db.getLibraryMangas().executeAsBlocking().associateBy { it.url }
                                val currentLibrary = getCurrentLibrary(currentLibraryManga)
                                Log.i(TAG, "Applying Version:${currentRemoteState.versionNumber} Guid:${currentRemoteState.guid}")
                                val addedOrUpdatedBetweenLocalAndLibrary = SyncDiffEngine.getAddedOrUpdated(currentLocalState.mangas, currentLibrary)
                                val addedOrUpdatedBetweenLibraryAndRemote = SyncDiffEngine.getAddedOrUpdated(currentLibrary, currentRemoteState.addedOrUpdatedMangas)
                                val removedBetweenLibraryAndRemote = currentLibrary.filter { currentRemoteState.removedMangas.contains(it.url) }

                                val addOrUpdateChangesToGetUpToSync = SyncDiffEngine.getSafeAddedOrUpdated(addedOrUpdatedBetweenLocalAndLibrary, addedOrUpdatedBetweenLibraryAndRemote)
                                val removedChangesToGetUpToSync = SyncDiffEngine.getSafeRemoved(addedOrUpdatedBetweenLocalAndLibrary, removedBetweenLibraryAndRemote)

                                Log.i(TAG, "removedChangesToGetUpToSync.size:${removedChangesToGetUpToSync.size} addOrUpdateChangesToGetUpToSync.size:${addOrUpdateChangesToGetUpToSync.size}")
                                if (removedChangesToGetUpToSync.any()) {
                                    val mangasToUnfavorite = removedChangesToGetUpToSync.map { currentLibraryManga.get(it.url)!! }
                                    for (mangaToRemove in mangasToUnfavorite) {
                                        mangaToRemove.favorite = false
                                        db.updateMangaFavorite(mangaToRemove).executeAsBlocking()
                                    }
                                }

                                if (addOrUpdateChangesToGetUpToSync.any()) {
                                    val mangaPairsToUpdate = addOrUpdateChangesToGetUpToSync.map { Pair(it, currentLibraryManga.get(it.url)) }
                                    for (mangaPairToUpdate in mangaPairsToUpdate) {
                                        var libraryManga: MangaImpl? = mangaPairToUpdate.second
                                        if (libraryManga == null) { // The manga has not been added to the current library
                                            val newManga = MangaImpl()
                                            newManga.url = mangaPairToUpdate.first.url
                                            newManga.title = mangaPairToUpdate.first.title
                                            newManga.source = mangaPairToUpdate.first.source
                                            newManga.favorite = true
                                            newManga.initialized = false
                                            db.insertManga(newManga).executeAsBlocking()
                                            libraryManga = newManga
                                            Log.i(TAG, "Adding new manga to db:${newManga.title} - id:${newManga.id}")
                                        }
                                        if (mangaPairToUpdate.first.chapters.any()) { // Any chapter updates to perform, or add
                                            Log.i(TAG, "Add/Update chapters.size:${mangaPairToUpdate.first.chapters.size}")
                                            val libraryChapters = db.getChapters(libraryManga).executeAsBlocking().associateBy { it.url }
                                            val chaptersToInsert = mutableListOf<Chapter>()
                                            val chaptersToUpdate = mutableListOf<Chapter>()
                                            for (chapterUpdate in mangaPairToUpdate.first.chapters) {
                                                var libraryChapter = libraryChapters.get(chapterUpdate.url)
                                                val isNew = libraryChapter == null
                                                if (libraryChapter == null) {
                                                    libraryChapter = ChapterImpl()
                                                }

                                                libraryChapter.url = chapterUpdate.url
                                                libraryChapter.name = chapterUpdate.name
                                                libraryChapter.date_upload = chapterUpdate.date_upload
                                                libraryChapter.chapter_number = chapterUpdate.chapter_number
                                                libraryChapter.manga_id = libraryManga.id
                                                libraryChapter.read = chapterUpdate.read
                                                libraryChapter.bookmark = chapterUpdate.bookmark
                                                libraryChapter.last_page_read = chapterUpdate.last_page_read
                                                libraryChapter.source_order = chapterUpdate.source_order
                                                Log.i(TAG,"Chapter Update '${libraryChapter.name}' read:${libraryChapter.read} last_page_read:${libraryChapter.last_page_read}")
                                                if(isNew){
                                                    chaptersToInsert.add(libraryChapter)
                                                }
                                                else{
                                                    chaptersToUpdate.add(libraryChapter)
                                                }
                                            }

                                            if(chaptersToInsert.any()){
                                                db.insertChapters(chaptersToInsert).executeAsBlocking()
                                            }
                                            if(chaptersToUpdate.any()){
                                                db.updateChaptersProgress(chaptersToUpdate).executeAsBlocking()
                                            }

                                        }
                                    }
                                }

                                currentLocalState.applyStateResponseDto(currentRemoteState)
                                SyncStateAccess.writeState(currentLocalState)
                            }
                        }

                        val currentLibraryManga = db.getLibraryMangas().executeAsBlocking().associateBy { it.url }
                        val currentLibrary = getCurrentLibrary(currentLibraryManga)
                        val remainingAddedAndUpdated = SyncDiffEngine.getAddedOrUpdated(currentLocalState.mangas, currentLibrary)
                        val remainingRemoved = SyncDiffEngine.getMissing(currentLocalState.mangas, currentLibrary)

                        if (remainingAddedAndUpdated.any() || remainingRemoved.any()) {
                            Log.i(TAG, "Changes to AddedAndUpdated:${remainingAddedAndUpdated.size} Removed:${remainingRemoved.size} - currentLocalState.mangas:${currentLocalState.mangas.size} currentLibrary:${currentLibrary.size}")
                            //GetRemoteSyncLock()
                            val latestChangesState = StateResponseDto(UUID.randomUUID(), currentLocalState.versionNumber + 1, remainingAddedAndUpdated, remainingRemoved.map { it.url })
                            val newResponseState = SyncApiAccess.sendStateResponse(latestChangesState)
                            currentLocalState.applyStateResponseDto(newResponseState)
                            SyncStateAccess.writeState(currentLocalState)
                            Log.i(TAG, "Changes Sync'd Version#:${newResponseState.versionNumber}")
                        }

                        SyncSettingsAccess.updateLastCheckedDate(Date.from(Instant.now()))
                        showResultNotification("SyncUpdateService complete")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Issue when doing SyncUpdate. $e")
                    }
                    Completable.complete()

                }
                .subscribeOn(Schedulers.io())
                .subscribe {
                    stopSelf(startId)
                }

        return Service.START_REDELIVER_INTENT
    }

    private fun getCurrentLibrary(libraryManga: Map<String, LibraryManga>): List<MangaResponseDto> {
        return libraryManga
                .filter { it.value.id != null }
                .map {
                    MangaResponseDto(it.value.url, it.value.title, it.value.source,
                            db.getChapters(it.value).executeAsBlocking().map {
                                ChapterResponseDto(it.url, it.name, it.date_upload, it.chapter_number, it.read, it.bookmark, it.last_page_read, it.source_order)
                            }.toMutableList())
                }
    }

    private fun showProgressNotification(manga: Manga, current: Int, total: Int) {
        notificationManager.notify(Notifications.ID_LIBRARY_PROGRESS, progressNotification
                .setContentTitle(manga.title)
                .setProgress(total, current, false)
                .build())
    }

    private fun showResultNotification(message: String) {
        notificationManager.notify(Notifications.ID_LIBRARY_RESULT, notification(Notifications.CHANNEL_LIBRARY) {
            setSmallIcon(R.drawable.ic_book_white_24dp)
            setLargeIcon(notificationBitmap)
            setContentTitle(getString(R.string.notification_new_chapters))
            setContentText(message)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(getNotificationIntent())
            setAutoCancel(true)
        })
    }

    private fun cancelProgressNotification() {
        notificationManager.cancel(Notifications.ID_LIBRARY_PROGRESS)
    }

    private fun getNotificationIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.action = MainActivity.SHORTCUT_RECENTLY_UPDATED
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

}

