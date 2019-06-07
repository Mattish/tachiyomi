package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*

class StateResponseDto(
        @SerializedName("Guid", alternate = ["guid"]) var guid: UUID,
        @SerializedName("VersionNumber", alternate = ["versionNumber", "versionnumber"]) var versionNumber: Int,
        @SerializedName("AddedOrUpdatedMangas", alternate = ["addedOrUpdatedMangas", "addedorupdatedmangas"]) var addedOrUpdatedMangas: List<MangaResponseDto>,
        @SerializedName("RemovedMangas", alternate = ["removedMangas", "removedmangas"]) var removedMangas: List<String>
)

