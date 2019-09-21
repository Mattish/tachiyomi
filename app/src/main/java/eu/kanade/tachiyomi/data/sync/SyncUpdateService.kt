package eu.kanade.tachiyomi.data.sync

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
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
import rx.Subscription
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.*
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.sync.model.*
import eu.kanade.tachiyomi.util.*
import rx.Completable
import java.io.File
import java.time.Instant
import java.util.*

class SyncUpdateService(
        val db: DatabaseHelper = Injekt.get(),
        val sourceManager: SourceManager = Injekt.get(),
        val preferences: PreferencesHelper = Injekt.get(),
        val downloadManager: DownloadManager = Injekt.get(),
        val gson: Gson = Injekt.get()
) : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private var subscription: Subscription? = null

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
    }

    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(this)
    }

    private val progressNotification by lazy {
        NotificationCompat.Builder(this, Notifications.CHANNEL_SYNC)
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_refresh_white_24dp_img)
                .setLargeIcon(notificationBitmap)
                .setContentText("Checking sync version")
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
        startForeground(Notifications.ID_SYNC_PROGRESS, progressNotification.build())
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
                        showSyncProgress(0,"Fetching RemoteVersion...")
                        val currentRemoteVersion = SyncApiAccess.getVersion()
                        val currentLocalState = SyncStateAccess.getState()
                        showSyncProgress(0,"Got RemoteVersion:" + currentRemoteVersion!!.versionNumber)
                        val requireRemoteSync = currentRemoteVersion != null && currentLocalState.versionNumber < currentRemoteVersion.versionNumber
                        if (requireRemoteSync) {
                            Log.i(TAG, "CurrentLocalVersion:${currentLocalState.versionNumber} CurrentRemoteVersion:${currentRemoteVersion?.versionNumber}")
                            val currentRemoteStates = SyncApiAccess.getCurrentStates(currentLocalState.versionNumber)
                            applyRemoteStates(currentLocalState, currentRemoteStates)
                        }

                        val currentLibraryManga = db.getLibraryMangas().executeAsBlocking().associateBy { it.url }
                        val currentLibrary = getCurrentLibrary(currentLibraryManga)
                        val remainingAddedAndUpdated = SyncDiffEngine.getAddedOrUpdated(currentLocalState.mangas, currentLibrary)
                        val remainingRemoved = SyncDiffEngine.getMissing(currentLocalState.mangas, currentLibrary)

                        val shouldDoNewSyncVersion = remainingAddedAndUpdated.any() || remainingRemoved.any()
                        if (shouldDoNewSyncVersion) {
                            Log.i(TAG, "Changes to AddedAndUpdated:${remainingAddedAndUpdated.size} Removed:${remainingRemoved.size} - currentLocalState.mangas:${currentLocalState.mangas.size} currentLibrary:${currentLibrary.size}")
                            //GetRemoteSyncLock()
                            val latestChangesState = StateResponseDto(currentLocalState.versionNumber + 1, remainingAddedAndUpdated, remainingRemoved.map { it.url }.toMutableList(), Date(System.currentTimeMillis()))
                            val newResponseState = SyncApiAccess.sendStateResponse(latestChangesState)
                            currentLocalState.applyStateResponseDto(newResponseState)
                            SyncStateAccess.writeState(currentLocalState)
                            Log.i(TAG, "Changes Sync'd Version#:${newResponseState.versionNumber}")
                        }

                        SyncSettingsAccess.updateLastCheckedDate(Date.from(Instant.now()))
                        val currentSyncState = SyncStateAccess.getState()
                        if (requireRemoteSync || shouldDoNewSyncVersion) {
                            showSuccessfulNotification("Successfully synced to Ver.${currentSyncState.versionNumber} dated '${currentSyncState.timestamp}'")
                        }
                        showSyncProgress(100,"Successfully synced to Ver.${currentSyncState.versionNumber} dated '${currentSyncState.timestamp}'")

                    } catch (e: Throwable) {
                        Log.e(TAG, "Issue when doing SyncUpdate. $e")
                        val file = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + "Tachiyomi", "sync/sync.log")
                        file.createNewFile();
                        file.appendText("Issue when doing SyncUpdate. $e\n");
                    }

                    Completable.complete()

                }
                .subscribeOn(Schedulers.io())
                .subscribe {
                    stopSelf(startId)
                }

        return Service.START_REDELIVER_INTENT
    }

    private fun getAggregateChanges(remoteStatesToApply: StatesResponseDto?): StateResponseDto{
        var allChangesState = StateResponseDto(remoteStatesToApply!!.states.last().versionNumber, mutableListOf(), mutableListOf(),remoteStatesToApply!!.states.last().timestamp)
        val lastStateVersion = remoteStatesToApply!!.states.last().versionNumber
        val totalAmountOfStatesToApply = remoteStatesToApply!!.states.count()
        for ((amountApplied, currentRemoteState) in remoteStatesToApply!!.states.withIndex()) {
            currentRemoteState.versionNumber
            val percProgress = ((amountApplied.toFloat()/totalAmountOfStatesToApply.toFloat())*100).toInt()
            showSyncProgress(percProgress,"Aggregating state #${currentRemoteState.versionNumber} of #$lastStateVersion")
            for(incomingRemoteMangaAddOrUpdate in currentRemoteState.addedOrUpdatedMangas){
                allChangesState.removedMangas.remove(incomingRemoteMangaAddOrUpdate.url)
                val existingManga = allChangesState.addedOrUpdatedMangas.firstOrNull{ manga -> manga == incomingRemoteMangaAddOrUpdate }
                if(existingManga == null){
                    allChangesState.addedOrUpdatedMangas.add(incomingRemoteMangaAddOrUpdate)
                }
                else{ // Manga has another change, so apply all the new chapter changes
                    for (updateChapter in incomingRemoteMangaAddOrUpdate.chapters){
                        val existingChapter = existingManga.chapters.firstOrNull{chap -> chap == updateChapter }
                        if(existingChapter == null){
                            existingManga.chapters.add(updateChapter)
                        }
                        else{
                            existingChapter.bookmark = updateChapter.bookmark
                            existingChapter.chapter_number = updateChapter.chapter_number
                            existingChapter.date_upload = updateChapter.date_upload
                            existingChapter.last_page_read = updateChapter.last_page_read
                            existingChapter.name = updateChapter.name
                            existingChapter.read = updateChapter.read
                            existingChapter.source_order = updateChapter.source_order
                        }
                    }
                }
            }
            for (removeMangaUrl in currentRemoteState.removedMangas){
                allChangesState.addedOrUpdatedMangas.removeIf { manga -> manga.url == removeMangaUrl }
                allChangesState.removedMangas.add(removeMangaUrl)
            }
        }
        return allChangesState
    }

    private fun applyRemoteStates(currentLocalState: AggregateSyncState, remoteStatesToApply: StatesResponseDto?){
        val aggregateChanges = getAggregateChanges(remoteStatesToApply)

        showSyncProgress(99,"Applying Aggregate State up to #${aggregateChanges.versionNumber}")
        val currentLibraryManga = db.getLibraryMangas().executeAsBlocking().associateBy { it.url }
        val currentLibrary = getCurrentLibrary(currentLibraryManga)
        Log.i(TAG, "Applying Version:${aggregateChanges.versionNumber}")
        val addedOrUpdatedBetweenLocalAndLibrary = SyncDiffEngine.getAddedOrUpdated(currentLocalState.mangas, currentLibrary)
        val addedOrUpdatedBetweenLibraryAndRemote = SyncDiffEngine.getAddedOrUpdated(currentLibrary, aggregateChanges.addedOrUpdatedMangas)
        val removedBetweenLibraryAndRemote = currentLibrary.filter { aggregateChanges.removedMangas.contains(it.url) }

        val addOrUpdateChangesToGetUpToSync = SyncDiffEngine.getSafeAddedOrUpdated(addedOrUpdatedBetweenLocalAndLibrary, addedOrUpdatedBetweenLibraryAndRemote)
        val removedChangesToGetUpToSync = SyncDiffEngine.getSafeRemoved(addedOrUpdatedBetweenLocalAndLibrary, removedBetweenLibraryAndRemote)

        Log.i(TAG, "removedChangesToGetUpToSync.size:${removedChangesToGetUpToSync.size} addOrUpdateChangesToGetUpToSync.size:${addOrUpdateChangesToGetUpToSync.size}")

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
                    newManga.initialized = true
                    newManga.author = mangaPairToUpdate.first.author
                    newManga.artist = mangaPairToUpdate.first.artist
                    newManga.thumbnail_url = mangaPairToUpdate.first.thumbnailUrl
                    newManga.last_update = mangaPairToUpdate.first.last_update
                    val result = db.insertManga(newManga).executeAsBlocking()
                    newManga.id = result.insertedId()
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
                        libraryChapter.date_fetch = chapterUpdate.date_fetch

                        Log.i(TAG, "Chapter Update '${libraryChapter.name}' read:${libraryChapter.read} last_page_read:${libraryChapter.last_page_read}")
                        if (isNew) {
                            chaptersToInsert.add(libraryChapter)
                        } else {
                            chaptersToUpdate.add(libraryChapter)
                        }
                    }

                    if (chaptersToInsert.any()) {
                        db.insertChapters(chaptersToInsert).executeAsBlocking()
                    }
                    if (chaptersToUpdate.any()) {
                        db.updateChaptersProgress(chaptersToUpdate).executeAsBlocking()
                        db.fixChaptersSourceOrder(chaptersToUpdate).executeAsBlocking()
                    }

                    //Chapter are being updated to marked as read, delete these if the setting is active.
                    if(preferences.removeAfterMarkedAsRead()) {
                        val chaptersMarkedAsRead = db.getChapters(libraryManga).executeAsBlocking().filter { c -> c.read }
                        downloadManager.enqueueDeleteChapters(chaptersMarkedAsRead, libraryManga)
                    }
                }
            }
        }

        Completable.fromCallable { downloadManager.deletePendingChapters() }
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()

        if (removedChangesToGetUpToSync.any()) {
            val mangasToUnfavorite = removedChangesToGetUpToSync.map { currentLibraryManga.get(it.url)!! }
            for (mangaToRemove in mangasToUnfavorite) {
                mangaToRemove.favorite = false
                db.updateMangaFavorite(mangaToRemove).executeAsBlocking()
            }
        }

        currentLocalState.applyStateResponseDto(aggregateChanges)
        SyncStateAccess.writeState(currentLocalState)
    }

    private fun getCurrentLibrary(libraryManga: Map<String, LibraryManga>): List<MangaResponseDto> {
        return libraryManga
                .filter { it.value.id != null }
                .map {
                    MangaResponseDto(it.value.url, it.value.title, it.value.source,it.value.thumbnail_url,it.value.last_update,it.value.artist,it.value.author,
                            db.getChapters(it.value).executeAsBlocking().map {
                                ChapterResponseDto(it.url, it.name, it.date_upload, it.date_fetch, it.chapter_number, it.read, it.bookmark, it.last_page_read, it.source_order)
                            }.toMutableList())
                }
    }

    private fun showSuccessfulNotification(message: String) {
        notificationManager.notify(Notifications.ID_SYNC_RESULT, notification(Notifications.CHANNEL_SYNC) {
            setSmallIcon(R.drawable.ic_book_white_24dp)
            setLargeIcon(notificationBitmap)
            setContentTitle(getString(R.string.notification_new_sync_version))
            setContentText(message)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(getNotificationIntent())
            setAutoCancel(true)
        })
    }

    private fun getNotificationIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.action = MainActivity.SHORTCUT_LIBRARY
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun showSyncProgress(progress: Int, content: String) {
        val intent = Intent(SyncConst.INTENT_FILTER).apply {
            putExtra(SyncConst.EXTRA_PROGRESS, progress)
            putExtra(SyncConst.EXTRA_CONTENT, content)
            putExtra(SyncConst.ACTION, SyncConst.ACTION_PROGRESS_DIALOG)
        }
        sendLocalBroadcast(intent)
    }

}

