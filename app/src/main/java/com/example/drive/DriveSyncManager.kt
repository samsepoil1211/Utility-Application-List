package com.example.drive

import com.example.config.AppConfig
import com.example.model.VaultFileEnvelope
import com.example.storage.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Optional Google Drive backup client using the restricted hidden appDataFolder scope ONLY:
 * https://www.googleapis.com/auth/drive.appdata
 *
 * Uploads and downloads the already end-to-end encrypted vault envelope as "dailylog-vault.json".
 * Google Drive only ever receives ciphertext, wrapped DEKs, salts, and IVs.
 * Conflict resolution uses last-write-wins on the authenticated ISO-8601 [updatedAt] timestamp.
 */
class DriveSyncManager(
    private val storage: VaultStorage,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    sealed class SyncResult {
        data class Uploaded(val fileId: String, val updatedAt: String) : SyncResult()
        data class Downloaded(val envelope: VaultFileEnvelope, val fileId: String) : SyncResult()
        data object Deleted : SyncResult()
        data class NotFound(val message: String) : SyncResult()
        data class NotConfigured(val message: String) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    fun isOAuthConfigured(): Boolean = AppConfig.DRIVE_OAUTH_CLIENT_ID.isNotBlank()

    suspend fun findBackupFileId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("name='${AppConfig.DRIVE_BACKUP_FILENAME}' and trashed=false", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime)&pageSize=1"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string().orEmpty()
            val files = JSONObject(body).optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) null else files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
        }
    }

    suspend fun uploadEncryptedVault(
        accessToken: String,
        envelope: VaultFileEnvelope,
        existingFileId: String? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val fileId = existingFileId ?: findBackupFileId(accessToken)
            val envelopeJson = storage.serializeEnvelope(envelope)
            val jsonMedia = "application/json; charset=utf-8".toMediaType()

            val request = if (fileId == null) {
                val metadata = JSONObject()
                    .put("name", AppConfig.DRIVE_BACKUP_FILENAME)
                    .put("parents", org.json.JSONArray().put("appDataFolder"))
                    .toString()

                val multipart = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toRequestBody(jsonMedia))
                    .addPart(envelopeJson.toRequestBody(jsonMedia))
                    .build()

                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipart)
                    .build()
            } else {
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&fields=id")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(envelopeJson.toRequestBody(jsonMedia))
                    .build()
            }

            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val savedId = JSONObject(body).optString("id", fileId.orEmpty())
                    SyncResult.Uploaded(savedId, envelope.vault.updatedAt)
                } else {
                    SyncResult.Error("Backup upload failed (HTTP ${resp.code}). Please check your Google Drive authorization.")
                }
            }
        } catch (_: Exception) {
            SyncResult.Error("Unable to reach Google Drive. Your encrypted vault remains safely saved offline.")
        }
    }

    suspend fun downloadEncryptedVault(accessToken: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val fileId = findBackupFileId(accessToken)
                ?: return@withContext SyncResult.NotFound("No ${AppConfig.DRIVE_BACKUP_FILENAME} backup found in Google Drive.")

            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext SyncResult.Error("Download failed (HTTP ${resp.code}).")
                }
                val parsed = storage.deserializeEnvelope(body)
                    ?: return@withContext SyncResult.Error("Downloaded backup file could not be parsed.")
                SyncResult.Downloaded(parsed, fileId)
            }
        } catch (_: Exception) {
            SyncResult.Error("Unable to connect to Google Drive while offline.")
        }
    }

    /**
     * Last-write-wins sync helper comparing ISO-8601 UTC updatedAt timestamps.
     */
    suspend fun syncLastWriteWins(accessToken: String, localEnvelope: VaultFileEnvelope): SyncResult {
        val remote = downloadEncryptedVault(accessToken)
        return when (remote) {
            is SyncResult.Downloaded -> {
                val remoteTs = remote.envelope.vault.updatedAt
                val localTs = localEnvelope.vault.updatedAt
                if (remoteTs > localTs) {
                    remote
                } else {
                    uploadEncryptedVault(accessToken, localEnvelope, remote.fileId)
                }
            }
            is SyncResult.NotFound -> uploadEncryptedVault(accessToken, localEnvelope, null)
            else -> remote
        }
    }

    suspend fun deleteCloudBackup(accessToken: String, knownFileId: String? = null): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                val fileId = knownFileId ?: findBackupFileId(accessToken)
                    ?: return@withContext SyncResult.NotFound("No cloud backup exists to delete.")

                val req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId")
                    .header("Authorization", "Bearer $accessToken")
                    .delete()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 404) SyncResult.Deleted
                    else SyncResult.Error("Could not delete cloud backup (HTTP ${resp.code}).")
                }
            } catch (_: Exception) {
                SyncResult.Error("Unable to reach Google Drive while offline.")
            }
        }
}
