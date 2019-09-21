package eu.kanade.tachiyomi.data.sync.model

import java.util.*

class AggregateSyncState(var versionNumber: Int, var timestamp: Date, val mangas: MutableList<MangaResponseDto>) {
    fun applyStateResponseDto(stateResponseDto: StateResponseDto) {
        for (mangaToApply in stateResponseDto.addedOrUpdatedMangas) {
            val existingManga = mangas.firstOrNull { it.url == mangaToApply.url }
            if (existingManga == null) {
                mangas.add(mangaToApply)
            } else {
                for (chapterToApply in mangaToApply.chapters) {
                    val existingChapter = existingManga.chapters.firstOrNull { it.url == chapterToApply.url }
                    if (existingChapter == null) {
                        existingManga.chapters.add(chapterToApply)
                    } else {
                        existingChapter.read = chapterToApply.read
                        existingChapter.last_page_read = chapterToApply.last_page_read
                    }
                }
            }
        }
        for (mangaToRemove in stateResponseDto.removedMangas) {
            val existingManga = mangas.firstOrNull { it.url == mangaToRemove }
            if(existingManga != null){
                mangas.remove(existingManga)
            }
        }
        versionNumber = stateResponseDto.versionNumber
        timestamp = stateResponseDto.timestamp
    }
}