package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*

class AccountCodeResponseDto(
        @SerializedName("Code", alternate = ["code"]) var code: String,
        @SerializedName("ValidUntil", alternate = ["validUntil","validuntil"]) var validUntil: Date
)