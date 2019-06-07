package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName

class MangaResponseDto(
        @SerializedName("Url", alternate = ["url"]) var url: String,
        @SerializedName("Title", alternate = ["title"]) var title: String,
        @SerializedName("Source", alternate = ["source"]) var source: Long,
        @SerializedName("Chapters", alternate = ["chapters"]) var chapters: MutableList<ChapterResponseDto>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val otherManga = other as MangaResponseDto
        return url == otherManga.url;
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}