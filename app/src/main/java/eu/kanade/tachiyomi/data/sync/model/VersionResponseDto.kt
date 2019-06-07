package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*


class VersionResponseDto(
        @SerializedName("Guid", alternate = ["guid"]) val guid: UUID,
        @SerializedName("VersionNumber", alternate = ["versionNumber", "versionnumber"]) var versionNumber: Int)

