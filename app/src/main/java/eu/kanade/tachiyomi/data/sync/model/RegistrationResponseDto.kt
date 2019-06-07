package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*

class RegistrationResponseDto(
        @SerializedName("DeviceId", alternate = ["deviceId","deviceid"]) var deviceId: UUID,
        @SerializedName("RecoveryCode", alternate = ["recoveryCode","recoverycode"]) var recoveryCode: UUID,
        @SerializedName("SecretToken", alternate = ["secretToken","secrettoken"]) var secretToken: String,
        @SerializedName("InitialState", alternate = ["initialState","initialstate"]) var initialState: StateResponseDto
)