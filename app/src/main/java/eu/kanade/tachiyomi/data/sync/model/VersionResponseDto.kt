package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*


class VersionResponseDto(
        @SerializedName("VersionNumber", alternate = ["versionNumber", "versionnumber"]) var versionNumber: Int)

