package eu.kanade.tachiyomi.data.sync.model

import com.google.gson.annotations.SerializedName
import java.util.*

class SyncSettingsDto(
        @SerializedName("Endpoint", alternate = ["endpoint"]) val endpoint: String,
        @SerializedName("DeviceId", alternate = ["deviceId","deviceid"]) val deviceId: UUID,
        @SerializedName("RecoveryCode", alternate = ["recoveryCode","recoverycode"]) val recoveryCode: UUID?,
        @SerializedName("AccessToken", alternate = ["accessToken","accesstoken"]) val accessToken: String?,
        @SerializedName("AutomaticEnabled", alternate = ["automaticEnabled","automaticenabled"]) val automaticEnabled: Boolean,
        @SerializedName("AutomaticMinutesInterval", alternate = ["automaticMinutesInterval","automaticminutesinterval"]) val automaticMinutesInterval: Int,
        @SerializedName("LastChecked", alternate = ["lastChecked","lastchecked"]) val lastChecked: Date?
        )