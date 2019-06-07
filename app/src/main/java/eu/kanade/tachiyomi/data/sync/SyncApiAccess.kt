package eu.kanade.tachiyomi.data.sync

import com.google.gson.Gson
import eu.kanade.tachiyomi.data.sync.model.*
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*

class SyncApiAccess {

    private constructor()

    companion object {
        private val networkService: NetworkHelper = Injekt.get()
        private val gson: Gson = Injekt.get()
        private val client: OkHttpClient = networkService.client.newBuilder().addInterceptor(SyncInterceptor()).build()
        private val jsonMime = MediaType.parse("application/json; charset=utf-8")

        fun getVersion(): VersionResponseDto? {
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/version")
                    .get()
                    .build()

            val response = client
                    .newCall(request)
                    .execute()

            val responseCode = response.code()
            if (response.code() == 401) {
                return null
            }
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseCode != 200 || responseBody.isEmpty()) {
                throw Exception("Invalid Response. Code:$responseCode")
            }
            return gson.fromJson<VersionResponseDto>(responseBody, VersionResponseDto::class.java)
        }

        fun getCurrentStates(fromVersion: Int, fromUuid: UUID): StatesResponseDto? {
            val client = client.newBuilder().build()
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/states?fromVersion=${fromVersion}&fromGuid=${fromUuid}")
                    .get()
                    .build()
            val response = client
                    .newCall(request)
                    .execute()
            if (response.code() == 401) {
                return null
            }
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseBody.isEmpty()) {
                throw Exception("Null Response")
            }
            return gson.fromJson<StatesResponseDto>(responseBody, StatesResponseDto::class.java)
        }

        fun sendStateResponse(stateResponseDto: StateResponseDto): StateResponseDto {
            val json = gson.toJson(stateResponseDto)
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/state")
                    .post(RequestBody.create(jsonMime, json))
                    .build()
            val response = client
                    .newCall(request)
                    .execute()

            val responseCode = response.code()
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseCode != 200 || responseBody.isEmpty()) {
                throw Exception("Invalid Response. Code:$responseCode")
            }
            return gson.fromJson<StateResponseDto>(responseBody, StateResponseDto::class.java)
        }

        fun sendRegistration(stateResponseDto: StateResponseDto): RegistrationResponseDto {
            val json = gson.toJson(stateResponseDto)
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/register")
                    .post(RequestBody.create(jsonMime, json))
                    .addHeader("Recovery-Code", UUID.randomUUID().toString())
                    .build()
            val response = client
                    .newCall(request)
                    .execute()

            val responseCode = response.code()
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseCode != 200 || responseBody.isEmpty()) {
                throw Exception("Invalid Response. Code:$responseCode")
            }
            return gson.fromJson<RegistrationResponseDto>(responseBody, RegistrationResponseDto::class.java)
        }

        fun getAccountCode(): AccountCodeResponseDto {
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/code")
                    .get()
                    .build()
            val response = client
                    .newCall(request)
                    .execute()

            val responseCode = response.code()
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseCode != 200 || responseBody.isEmpty()) {
                throw Exception("Invalid Response. Code:$responseCode")
            }
            return gson.fromJson<AccountCodeResponseDto>(responseBody, AccountCodeResponseDto::class.java)
        }

        fun sendRecoveryRegistration(recoveryCode: UUID): RegistrationResponseDto {
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/register/recovery")
                    .post(RequestBody.create(jsonMime, ""))
                    .addHeader("Recovery-Code", recoveryCode.toString())
                    .build()
            val response = client
                    .newCall(request)
                    .execute()

            val responseCode = response.code()
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseCode != 200 || responseBody.isEmpty()) {
                throw Exception("Invalid Response. Code:$responseCode")
            }
            return gson.fromJson<RegistrationResponseDto>(responseBody, RegistrationResponseDto::class.java)
        }

        fun sendAccountCodeRegistration(recoveryCode: UUID, accountCode: String): RegistrationResponseDto {
            val request = Request.Builder()
                    .url("${SyncSettingsAccess.getSettings().endpoint}/register/code")
                    .post(RequestBody.create(jsonMime, ""))
                    .addHeader("Recovery-Code", recoveryCode.toString())
                    .addHeader("Account-Code", accountCode.toString())
                    .build()
            val response = client
                    .newCall(request)
                    .execute()

            val responseCode = response.code()
            val responseBody = response.body()?.string().orEmpty()
            response.close()
            if (responseCode != 200 || responseBody.isEmpty()) {
                throw Exception("Invalid Response. Code:$responseCode")
            }
            return gson.fromJson<RegistrationResponseDto>(responseBody, RegistrationResponseDto::class.java)
        }
    }
}