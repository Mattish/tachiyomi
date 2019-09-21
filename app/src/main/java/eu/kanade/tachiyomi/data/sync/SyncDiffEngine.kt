package eu.kanade.tachiyomi.data.sync

import android.util.Log
import eu.kanade.tachiyomi.data.sync.SyncUpdateService.Companion.TAG
import eu.kanade.tachiyomi.data.sync.model.ChapterResponseDto
import eu.kanade.tachiyomi.data.sync.model.MangaResponseDto


class SyncDiffEngine {

    companion object {
        fun getAddedOrUpdated(currentMangas: List<MangaResponseDto>, incomingMangas: List<MangaResponseDto>): MutableList<MangaResponseDto> {
            val currentMangasMap = currentMangas.associateBy { it.url }
            val changedManga = mutableListOf<MangaResponseDto>()

            for (incomingManga in incomingMangas) {
                val currentManga = currentMangasMap.get(incomingManga.url)
                if (currentManga == null) {
                    changedManga.add(incomingManga)
                } else {
                    val changedChapters = getDifferentChapters(currentManga.chapters, incomingManga.chapters).toMutableList()
                    if (changedChapters.any()) {
                        changedManga.add(MangaResponseDto(incomingManga.url, incomingManga.title, incomingManga.source,incomingManga.thumbnailUrl,incomingManga.last_update,incomingManga.artist,incomingManga.author, changedChapters))
                    }
                }
            }
            return changedManga
        }

        fun getMissing(currentMangas: List<MangaResponseDto>, incomingMangas: List<MangaResponseDto>): List<MangaResponseDto> {
            val incomingMangasMap = incomingMangas.associateBy { it.url }
            val missingManga = mutableListOf<MangaResponseDto>()

            for (currentManga in currentMangas) {
                if (!incomingMangasMap.containsKey(currentManga.url)) {
                    missingManga.add(currentManga)
                }
            }
            return missingManga
        }

        fun getSafeAddedOrUpdated(localToLibAddedOrUpdated: List<MangaResponseDto>, libToRemoteAddedOrUpdated: List<MangaResponseDto>): List<MangaResponseDto> {
            if (!localToLibAddedOrUpdated.any()) return libToRemoteAddedOrUpdated // We don't have any local changes
            val safeToRemoteChanges = mutableListOf<MangaResponseDto>()
            val localToLibLookup = localToLibAddedOrUpdated.flatMap { it.chapters }.map { Pair(it.url, it) }.toMap()

            for (potentialRemoteMangaChange in libToRemoteAddedOrUpdated) {
                val potentialMangaChanges = mutableListOf<ChapterResponseDto>()
                for (potentialRemoteChapterChange in potentialRemoteMangaChange.chapters) {
                    val localToLibChange = localToLibLookup.get(potentialRemoteChapterChange.url)
                    if (localToLibChange == null || isChapterChangeSafe(localToLibChange, potentialRemoteChapterChange)) { // There is some local change to the chapter
                        potentialMangaChanges.add(potentialRemoteChapterChange)
                    } else {
                        Log.w(TAG, "Not updating manga:${potentialRemoteMangaChange.url} chapter:${potentialRemoteChapterChange.url} due to potentially unsafe update")
                    }
                }
                if (potentialMangaChanges.any()) {
                    safeToRemoteChanges.add(MangaResponseDto(potentialRemoteMangaChange.url, potentialRemoteMangaChange.title, potentialRemoteMangaChange.source, potentialRemoteMangaChange.thumbnailUrl,
                            potentialRemoteMangaChange.last_update,potentialRemoteMangaChange.artist,potentialRemoteMangaChange.author,potentialMangaChanges))
                }
            }
            return safeToRemoteChanges
        }

        fun getSafeRemoved(localToLibAddedOrUpdated: List<MangaResponseDto>, removedBetweenLibraryAndRemote: List<MangaResponseDto>): List<MangaResponseDto> {
            val safeToRemove = mutableListOf<MangaResponseDto>()
            val localToLibAddedOrUpdatedMap = localToLibAddedOrUpdated.map { Pair(it.url, it) }.toMap()

            for (potentialToRemove in removedBetweenLibraryAndRemote) {
                if (!localToLibAddedOrUpdatedMap.containsKey(potentialToRemove.url)) {
                    safeToRemove.add(potentialToRemove)
                }
            }
            return safeToRemove
        }

        private fun isChapterChangeSafe(libChapter: ChapterResponseDto, remoteChapter: ChapterResponseDto): Boolean {
            return !(libChapter.read && !remoteChapter.read) && (remoteChapter.last_page_read >= libChapter.last_page_read)
        }

        private fun getDifferentChapters(currentChapters: List<ChapterResponseDto>, incomingChapters: List<ChapterResponseDto>): List<ChapterResponseDto> {
            val changedChapters = mutableListOf<ChapterResponseDto>()

            val currentChaptersMap = currentChapters.associateBy { it.url }
            for (currentSyncChapter in incomingChapters) {
                val existingSyncChapter = currentChaptersMap.get(currentSyncChapter.url)
                if (existingSyncChapter == null || isChapterDifferent(existingSyncChapter, currentSyncChapter)) {
                    changedChapters.add(currentSyncChapter)
                }
            }
            return changedChapters
        }

        private fun isChapterDifferent(existing: ChapterResponseDto, current: ChapterResponseDto): Boolean {
            return existing.read != current.read || existing.last_page_read != current.last_page_read
        }
    }
}