package eu.kanade.tachiyomi.data.sync

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SyncInterceptor(val gson: Gson = Injekt.get()) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val authRequest = originalRequest.newBuilder()

        authRequest.addHeader("Device-ID", SyncSettingsAccess.getSettings().deviceId.toString())
        if (SyncSettingsAccess.isRegistered()) {
            authRequest.addHeader("Authorization", SyncSettingsAccess.getSettings().accessToken!!)
        }

        return chain.proceed(authRequest.build())
    }
}