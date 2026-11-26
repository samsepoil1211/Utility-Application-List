package com.example.auth

import com.example.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional online identifier verification using Firebase Authentication REST endpoints
 * when [AppConfig.FIREBASE_AUTH_CONFIG] is populated.
 *
 * Security boundary: Firebase NEVER receives the Data Encryption Key (DEK), Recovery Key,
 * TOTP secret, or any health/check-in data. Local offline accounts work fully without verification.
 */
class OnlineVerifier(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    sealed class VerifyResult {
        data class Sent(val message: String) : VerifyResult()
        data class NotConfigured(val message: String) : VerifyResult()
        data class Failed(val message: String) : VerifyResult()
    }

    suspend fun requestEmailVerification(email: String, authPasswordForOnlineOnly: String): VerifyResult =
        withContext(Dispatchers.IO) {
            val cfg = AppConfig.FIREBASE_AUTH_CONFIG
            if (!cfg.isConfigured) {
                return@withContext VerifyResult.NotConfigured(
                    "Online verification is optional and not configured in AppConfig. Your local encrypted ${AppConfig.APP_NAME_PUBLIC} account works fully offline."
                )
            }
            try {
                // Step 1: signUp or signInWithPassword to obtain an idToken (only email, never vault data)
                val signUpBody = JSONObject()
                    .put("email", email)
                    .put("password", authPasswordForOnlineOnly)
                    .put("returnSecureToken", true)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val signUpReq = Request.Builder()
                    .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${cfg.apiKey}")
                    .post(signUpBody)
                    .build()

                val idToken = httpClient.newCall(signUpReq).execute().use { resp ->
                    val json = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        JSONObject(json).optString("idToken")
                    } else {
                        // Fall back to signInWithPassword if account already exists in Firebase
                        val signInReq = Request.Builder()
                            .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${cfg.apiKey}")
                            .post(signUpBody)
                            .build()
                        httpClient.newCall(signInReq).execute().use { r2 ->
                            if (r2.isSuccessful) JSONObject(r2.body?.string().orEmpty()).optString("idToken")
                            else ""
                        }
                    }
                }

                if (idToken.isBlank()) {
                    return@withContext VerifyResult.Failed("Could not start online verification. Check your connection and try again.")
                }

                // Step 2: trigger verification email
                val oobBody = JSONObject()
                    .put("requestType", "VERIFY_EMAIL")
                    .put("idToken", idToken)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val oobReq = Request.Builder()
                    .url("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${cfg.apiKey}")
                    .post(oobBody)
                    .build()

                httpClient.newCall(oobReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        VerifyResult.Sent("Verification link sent to $email. Your vault keys stay strictly on this device.")
                    } else {
                        VerifyResult.Failed("Verification service declined the request. You can continue using ${AppConfig.APP_NAME_PUBLIC} offline.")
                    }
                }
            } catch (_: Exception) {
                VerifyResult.Failed("Unable to reach verification service while offline.")
            }
        }
}
