package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*

class StatesResponseDto(
        @SerializedName("FromVersionNumber", alternate = ["fromVersionNumber", "fromversionnumber"]) var FromVersionNumber: Int,
        @SerializedName("States", alternate = ["states"]) var states: List<StateResponseDto>
)