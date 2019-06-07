package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName

class ChapterResponseDto(
        @SerializedName("Url", alternate = ["url"]) var url: String,
        @SerializedName("Name", alternate = ["name"]) var name: String,
        @SerializedName("DateUpload", alternate = ["dateUpload","dateupload"]) var date_upload: Long,
        @SerializedName("ChapterNumber", alternate = ["chapterNumber","chapternumber"]) var chapter_number: Float,
        @SerializedName("Read", alternate = ["read"]) var read: Boolean,
        @SerializedName("Bookmark", alternate = ["bookmark"]) var bookmark: Boolean,
        @SerializedName("LastPageRead", alternate = ["lastPageRead","lastpageread"]) var last_page_read: Int,
        @SerializedName("SourceOrder", alternate = ["sourceOrder", "sourceorder"]) var source_order: Int
        ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val otherChapter = other as ChapterResponseDto
        return url == otherChapter.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}