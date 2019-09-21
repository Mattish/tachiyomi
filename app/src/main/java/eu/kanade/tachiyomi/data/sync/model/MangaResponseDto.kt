package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName

class MangaResponseDto(
        @SerializedName("Url", alternate = ["url"]) var url: String,
        @SerializedName("Title", alternate = ["title"]) var title: String,
        @SerializedName("Source", alternate = ["source"]) var source: Long,
        @SerializedName("ThumbnailUrl", alternate = ["thumbnailUrl","thumbnailurl"]) var thumbnailUrl: String?,
        @SerializedName("LastUpdate", alternate = ["lastUpdate","lastupdate"]) var last_update: Long,
        @SerializedName("Artist", alternate = ["artist"]) var artist: String?,
        @SerializedName("Author", alternate = ["author"]) var author: String?,
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