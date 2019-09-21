package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*

class StateResponseDto(
        @SerializedName("VersionNumber", alternate = ["versionNumber", "versionnumber"]) var versionNumber: Int,
        @SerializedName("AddedOrUpdatedMangas", alternate = ["addedOrUpdatedMangas", "addedorupdatedmangas"]) var addedOrUpdatedMangas: MutableList<MangaResponseDto>,
        @SerializedName("RemovedMangas", alternate = ["removedMangas", "removedmangas"]) var removedMangas: MutableList<String>,
        @SerializedName("Timestamp", alternate = ["timestamp"]) var timestamp: Date
)

