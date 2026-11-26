package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.config.AppConfig
import com.example.model.ReminderPrefs
import com.example.totp.TotpEngine
import com.example.ui.components.AmberPrimaryButton
import com.example.ui.components.HairlineDivider
import com.example.ui.components.InstrumentActionRow
import com.example.ui.components.InstrumentSectionPanel
import com.example.ui.components.InstrumentTextField
import com.example.ui.components.InstrumentToggleRow
import com.example.ui.components.KeyDerivationBanner
import com.example.ui.components.QrCodeView
import com.example.ui.components.QuietOutlineButton
import com.example.ui.theme.LocalInstrumentColors
import com.example.viewmodel.DailyLogUiState
import com.example.viewmodel.DailyLogViewModel
import java.util.Locale

private enum class SettingsDialogKind {
    None,
    ChangePassword,
    RegenerateBackupCodes,
    ChangeAuthenticatorReAuth,
    Disable2Fa,
    OnlineVerify,
    DriveConnect,
    DeleteDriveBackup,
    EraseAllData,
    ReminderTimePicker
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: DailyLogUiState,
    viewModel: DailyLogViewModel,
    onEditQuitPlan: () -> Unit
) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    val payload = state.payload ?: return
    val header = state.accountHeader ?: return

    var activeDialog by remember { mutableStateOf(SettingsDialogKind.None) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    // Notification runtime permission launcher for Android 13+
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateReminders(payload.reminders.copy(enabled = true))
        } else {
            viewModel.showToast("Notification permission is needed to schedule reminders.", isError = true)
        }
    }

    // Zero-permission SAF document creator to export dailylog-vault.json locally
    val exportVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = viewModel.getEncryptedVaultJsonForExport()
            if (json != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.showToast("Encrypted ${AppConfig.DRIVE_BACKUP_FILENAME} exported.")
                } catch (_: Exception) {
                    viewModel.showToast("Could not write backup file.", isError = true)
                }
            }
        }
    }

    val remainingBackupCount = payload.backupCodes.count { !it.used }
    val reminderTimeLabel = formatClockTime(payload.reminders.dailyHour, payload.reminders.dailyMinute)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${AppConfig.APP_NAME_PUBLIC} · End-to-end encrypted offline vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            // 1. Account
            InstrumentSectionPanel(title = "Account") {
                InstrumentActionRow(
                    title = header.identifier,
                    subtitle = if (header.onlineVerified) {
                        "Verified online · vault keys remain strictly on-device"
                    } else {
                        "Local offline account · optional online verification available"
                    },
                    valueText = if (header.onlineVerified) "Verified" else "Not verified yet",
                    valueColor = if (header.onlineVerified) colors.mint else colors.amber,
                    onClick = {
                        dialogError = null
                        activeDialog = SettingsDialogKind.OnlineVerify
                    }
                )
                HairlineDivider()
                InstrumentActionRow(
                    title = "Edit quit plan",
                    subtitle = "Quit date, weekly spend, grams per day, currency, and reason",
                    onClick = onEditQuitPlan,
                    modifier = Modifier.testTag("settings_edit_plan_row")
                )
            }

            // 2. Security & 2FA
            InstrumentSectionPanel(title = "Security & 2FA") {
                InstrumentActionRow(
                    title = "Change password",
                    subtitle = "Re-wraps your 256-bit data key without re-encrypting history",
                    onClick = {
                        dialogError = null
                        activeDialog = SettingsDialogKind.ChangePassword
                    },
                    modifier = Modifier.testTag("settings_change_password_row")
                )
                HairlineDivider()
                InstrumentActionRow(
                    title = "Two-factor backup codes",
                    subtitle = if (payload.totpEnabled) "Single-use hashed codes stored inside vault" else "Enable authenticator to generate codes",
                    valueText = if (payload.totpEnabled) "$remainingBackupCount left" else "Off",
                    valueColor = if (remainingBackupCount <= 2 && payload.totpEnabled) colors.coral else colors.muted,
                    onClick = {
                        dialogError = null
                        if (payload.totpEnabled) {
                            activeDialog = SettingsDialogKind.RegenerateBackupCodes
                        } else {
                            activeDialog = SettingsDialogKind.ChangeAuthenticatorReAuth
                        }
                    }
                )
                HairlineDivider()
                InstrumentActionRow(
                    title = if (payload.totpEnabled) "Change authenticator" else "Enable authenticator (TOTP)",
                    subtitle = "Generate a fresh offline QR code and setup key",
                    onClick = {
                        dialogError = null
                        activeDialog = SettingsDialogKind.ChangeAuthenticatorReAuth
                    }
                )
                if (payload.totpEnabled) {
                    HairlineDivider()
                    InstrumentActionRow(
                        title = "Disable 2FA",
                        subtitle = "Reduces protection on sign-in and password reset",
                        danger = true,
                        onClick = {
                            dialogError = null
                            activeDialog = SettingsDialogKind.Disable2Fa
                        }
                    )
                }
                HairlineDivider()
                InstrumentActionRow(
                    title = "Lock now",
                    subtitle = "Wipes encryption key from memory immediately",
                    onClick = { viewModel.lockNow(forceFull2fa = false) },
                    modifier = Modifier.testTag("settings_lock_now_row")
                )
            }

            // 3. Notifications (always titled "Daily Log", neutral generic text)
            InstrumentSectionPanel(title = "Notifications") {
                InstrumentToggleRow(
                    title = "Quiet ${AppConfig.APP_NAME_PUBLIC} reminders",
                    subtitle = "Generic text only — never mentions weed or quitting",
                    checked = payload.reminders.enabled,
                    onCheckedChange = { wantEnable ->
                        if (!wantEnable) {
                            viewModel.updateReminders(payload.reminders.copy(enabled = false))
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateReminders(payload.reminders.copy(enabled = true))
                            }
                        }
                    }
                )
                if (payload.reminders.enabled) {
                    HairlineDivider()
                    InstrumentActionRow(
                        title = "Daily check-in time",
                        valueText = reminderTimeLabel,
                        valueColor = colors.amber,
                        onClick = { activeDialog = SettingsDialogKind.ReminderTimePicker }
                    )
                    HairlineDivider()
                    InstrumentToggleRow(
                        title = "Peak craving heads-up",
                        subtitle = state.stats?.peakCravingLabel?.let {
                            "30 minutes before your peak window ($it)"
                        } ?: "Activates automatically once you log cravings",
                        checked = payload.reminders.peakHourHeadsUp,
                        onCheckedChange = {
                            viewModel.updateReminders(payload.reminders.copy(peakHourHeadsUp = it))
                        }
                    )
                    HairlineDivider()
                    InstrumentToggleRow(
                        title = "Weekly summary",
                        subtitle = "Sunday evening quiet prompt",
                        checked = payload.reminders.weeklySummary,
                        onCheckedChange = {
                            viewModel.updateReminders(payload.reminders.copy(weeklySummary = it))
                        }
                    )
                    HairlineDivider()
                    InstrumentToggleRow(
                        title = "Milestone alerts",
                        subtitle = "Neutral alert when you pass a recovery mark",
                        checked = payload.reminders.milestoneAlerts,
                        onCheckedChange = {
                            viewModel.updateReminders(payload.reminders.copy(milestoneAlerts = it))
                        }
                    )
                }
            }

            // 4. Google Drive Backup (optional, online only, appDataFolder)
            InstrumentSectionPanel(title = "Google Drive backup") {
                if (state.isDriveSyncing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.amber)
                        Text("Syncing encrypted ${AppConfig.DRIVE_BACKUP_FILENAME}...", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    HairlineDivider()
                }
                InstrumentActionRow(
                    title = if (state.driveMeta.accessToken != null) "Back up now" else "Connect Google Drive",
                    subtitle = state.driveMeta.lastBackupIso?.let { "Last backup: ${it.take(19).replace('T', ' ')} UTC" }
                        ?: "Hidden appDataFolder scope only · Google sees ciphertext only",
                    valueText = if (state.driveMeta.accessToken != null) "Connected" else "Offline",
                    valueColor = if (state.driveMeta.accessToken != null) colors.mint else colors.muted,
                    onClick = {
                        if (state.driveMeta.accessToken != null) {
                            viewModel.triggerDriveBackupNow()
                        } else {
                            activeDialog = SettingsDialogKind.DriveConnect
                        }
                    }
                )
                HairlineDivider()
                InstrumentToggleRow(
                    title = "Auto-backup when online",
                    subtitle = "Debounced sync using last-write-wins on updatedAt",
                    checked = payload.uiPrefs.autoBackupDrive,
                    onCheckedChange = { viewModel.updateAutoBackup(it) }
                )
                HairlineDivider()
                InstrumentActionRow(
                    title = "Export encrypted backup file",
                    subtitle = "Save ${AppConfig.DRIVE_BACKUP_FILENAME} locally via Android file picker",
                    onClick = { exportVaultLauncher.launch(AppConfig.DRIVE_BACKUP_FILENAME) }
                )
                if (state.driveMeta.fileId != null || state.driveMeta.accessToken != null) {
                    HairlineDivider()
                    InstrumentActionRow(
                        title = "Delete cloud backup",
                        subtitle = "Removes ${AppConfig.DRIVE_BACKUP_FILENAME} from Google Drive",
                        danger = true,
                        onClick = { activeDialog = SettingsDialogKind.DeleteDriveBackup }
                    )
                }
            }

            // 5. Appearance & Motion
            InstrumentSectionPanel(title = "Appearance & motion") {
                InstrumentToggleRow(
                    title = "Dark theme (Dusk)",
                    subtitle = "Switch off for the matching high-contrast light theme",
                    checked = state.darkTheme,
                    onCheckedChange = { viewModel.updateTheme(it) },
                    modifier = Modifier.testTag("settings_dark_theme_toggle")
                )
                HairlineDivider()
                InstrumentToggleRow(
                    title = "Reduce motion",
                    subtitle = "Disables springs, breathing glow, and entrance sweeps except fades",
                    checked = state.reduceMotion,
                    onCheckedChange = { viewModel.updateReduceMotion(it) }
                )
            }

            // 6. Session & Data
            InstrumentSectionPanel(title = "Session & data") {
                InstrumentActionRow(
                    title = "Sign out",
                    subtitle = "Locks vault and requires full sign-in with 2FA next time",
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.testTag("settings_sign_out_row")
                )
                HairlineDivider()
                InstrumentActionRow(
                    title = "Erase all local data",
                    subtitle = "Permanently wipes encrypted vault and resets this device",
                    danger = true,
                    onClick = {
                        dialogError = null
                        activeDialog = SettingsDialogKind.EraseAllData
                    },
                    modifier = Modifier.testTag("settings_erase_data_row")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // -------------------------------------------------------------------------
    // DIALOGS FOR SENSITIVE ACTIONS
    // -------------------------------------------------------------------------

    when (activeDialog) {
        SettingsDialogKind.ChangePassword -> {
            ChangePasswordDialog(
                totpEnabled = payload.totpEnabled,
                busy = state.isCryptoBusy,
                busyLabel = state.cryptoBusyLabel,
                error = dialogError,
                onDismiss = { activeDialog = SettingsDialogKind.None },
                onSubmit = { cur, code, next, conf ->
                    dialogError = null
                    viewModel.changePassword(cur, code, next, conf) { ok, err ->
                        if (ok) activeDialog = SettingsDialogKind.None else dialogError = err
                    }
                }
            )
        }

        SettingsDialogKind.RegenerateBackupCodes -> {
            ReAuthConfirmDialog(
                title = "Regenerate backup codes",
                body = "This invalidates all previous single-use backup codes and creates 10 fresh codes.",
                confirmText = "Regenerate",
                danger = false,
                totpEnabled = payload.totpEnabled,
                busy = state.isCryptoBusy,
                error = dialogError,
                onDismiss = { activeDialog = SettingsDialogKind.None },
                onConfirm = { pw, code ->
                    dialogError = null
                    viewModel.regenerateBackupCodes(pw, code) { err -> dialogError = err }
                    // Dialog closes automatically once newlyGeneratedBackupCodes appears or on manual dismiss
                }
            )
        }

        SettingsDialogKind.ChangeAuthenticatorReAuth -> {
            ReAuthConfirmDialog(
                title = if (payload.totpEnabled) "Change authenticator" else "Enable authenticator",
                body = "Confirm your password${if (payload.totpEnabled) " and current 2FA code" else ""} to generate a new offline TOTP secret.",
                confirmText = "Continue",
                danger = false,
                totpEnabled = payload.totpEnabled,
                busy = state.isCryptoBusy,
                error = dialogError,
                onDismiss = { activeDialog = SettingsDialogKind.None },
                onConfirm = { pw, code ->
                    dialogError = null
                    viewModel.beginChangeAuthenticator(pw, code) { err -> dialogError = err }
                }
            )
        }

        SettingsDialogKind.Disable2Fa -> {
            ReAuthConfirmDialog(
                title = "Disable two-factor authentication",
                body = "Warning: Disabling 2FA removes the second verification factor on sign-in, password resets, and cloud restores. Are you sure?",
                confirmText = "Disable 2FA",
                danger = true,
                totpEnabled = payload.totpEnabled,
                busy = state.isCryptoBusy,
                error = dialogError,
                onDismiss = { activeDialog = SettingsDialogKind.None },
                onConfirm = { pw, code ->
                    dialogError = null
                    viewModel.disableTwoFactor(pw, code) { ok, err ->
                        if (ok) activeDialog = SettingsDialogKind.None else dialogError = err
                    }
                }
            )
        }

        SettingsDialogKind.EraseAllData -> {
            ReAuthConfirmDialog(
                title = "Erase all local data",
                body = "This permanently overwrites and deletes your local encrypted ${AppConfig.APP_NAME_PUBLIC} vault. Unless you have a Google Drive or exported backup plus your password/recovery key, your data cannot be recovered.",
                confirmText = "Erase permanently",
                danger = true,
                totpEnabled = payload.totpEnabled,
                busy = state.isCryptoBusy,
                error = dialogError,
                onDismiss = { activeDialog = SettingsDialogKind.None },
                onConfirm = { pw, code ->
                    dialogError = null
                    viewModel.eraseAllLocalData(pw, code) { ok, err ->
                        if (ok) activeDialog = SettingsDialogKind.None else dialogError = err
                    }
                }
            )
        }

        SettingsDialogKind.OnlineVerify -> {
            var onlinePw by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { activeDialog = SettingsDialogKind.None },
                containerColor = colors.panel,
                title = { Text("Online account verification", color = colors.text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Optional: verify ownership of ${header.identifier} via Firebase Authentication when online. Firebase never receives your encryption key, recovery key, or log entries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                        if (!AppConfig.FIREBASE_AUTH_CONFIG.isConfigured) {
                            Text(
                                text = "Firebase Auth config in AppConfig.kt is currently empty. Your offline account works fully without verification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.amber
                            )
                        } else {
                            InstrumentTextField(
                                value = onlinePw,
                                onValueChange = { onlinePw = it },
                                label = "Verification account password",
                                isPassword = true
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.requestOnlineVerification(onlinePw)
                            activeDialog = SettingsDialogKind.None
                        }
                    ) { Text(if (AppConfig.FIREBASE_AUTH_CONFIG.isConfigured) "Send verification" else "OK", color = colors.amber) }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = SettingsDialogKind.None }) { Text("Close", color = colors.muted) }
                }
            )
        }

        SettingsDialogKind.DriveConnect -> {
            var token by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { activeDialog = SettingsDialogKind.None },
                containerColor = colors.panel,
                title = { Text("Connect Google Drive", color = colors.text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Uploads only encrypted ciphertext (${AppConfig.DRIVE_BACKUP_FILENAME}) to your hidden appDataFolder using scope ${AppConfig.DRIVE_SCOPE_APPDATA}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                        if (AppConfig.DRIVE_OAUTH_CLIENT_ID.isBlank()) {
                            Text(
                                text = "Set DRIVE_OAUTH_CLIENT_ID in AppConfig.kt for interactive OAuth, or provide a temporary drive.appdata OAuth access token below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.amber
                            )
                        }
                        InstrumentTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = "OAuth access token (drive.appdata)",
                            placeholder = "ya29...."
                        )
                        InstrumentTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Google account label (optional)",
                            placeholder = "you@gmail.com"
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = token.isNotBlank(),
                        onClick = {
                            viewModel.connectDriveWithToken(token, email.takeIf { it.isNotBlank() })
                            activeDialog = SettingsDialogKind.None
                        }
                    ) { Text("Connect & back up", color = colors.amber) }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = SettingsDialogKind.None }) { Text("Cancel", color = colors.muted) }
                }
            )
        }

        SettingsDialogKind.DeleteDriveBackup -> {
            AlertDialog(
                onDismissRequest = { activeDialog = SettingsDialogKind.None },
                containerColor = colors.panel,
                title = { Text("Delete cloud backup?", color = colors.text) },
                text = {
                    Text(
                        text = "This removes ${AppConfig.DRIVE_BACKUP_FILENAME} from your Google Drive appDataFolder. Your local encrypted vault on this device will not be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            activeDialog = SettingsDialogKind.None
                            viewModel.deleteDriveBackup()
                        }
                    ) { Text("Delete backup", color = colors.coral) }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = SettingsDialogKind.None }) { Text("Cancel", color = colors.muted) }
                }
            )
        }

        SettingsDialogKind.ReminderTimePicker -> {
            val timeState = rememberTimePickerState(
                initialHour = payload.reminders.dailyHour,
                initialMinute = payload.reminders.dailyMinute,
                is24Hour = false
            )
            Dialog(onDismissRequest = { activeDialog = SettingsDialogKind.None }) {
                Column(
                    modifier = Modifier
                        .background(colors.panel, RoundedCornerShape(18.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Daily check-in time", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Spacer(modifier = Modifier.height(14.dp))
                    TimePicker(state = timeState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { activeDialog = SettingsDialogKind.None }) { Text("Cancel", color = colors.muted) }
                        TextButton(
                            onClick = {
                                viewModel.updateReminders(
                                    payload.reminders.copy(
                                        dailyHour = timeState.hour,
                                        dailyMinute = timeState.minute
                                    )
                                )
                                activeDialog = SettingsDialogKind.None
                            }
                        ) { Text("Save", color = colors.amber) }
                    }
                }
            }
        }

        SettingsDialogKind.None -> Unit
    }

    // Show newly regenerated backup codes ONCE
    state.newlyGeneratedBackupCodes?.let { codes ->
        if (activeDialog == SettingsDialogKind.RegenerateBackupCodes) {
            activeDialog = SettingsDialogKind.None
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissBackupCodesDialog() },
            containerColor = colors.panel,
            title = { Text("New backup codes (shown once)", color = colors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Save these 10 single-use codes offline now. Previous codes are no longer valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        codes.forEach { code ->
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.amber,
                                modifier = Modifier
                                    .background(colors.panelElevated, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            copyToClipboard(context, "Daily Log backup codes", codes.joinToString("\n"))
                            viewModel.showToast("Backup codes copied.")
                        }) { Text("Copy", color = colors.lilac) }
                        TextButton(onClick = {
                            sharePlainText(context, "${AppConfig.APP_NAME_PUBLIC} Backup Codes", codes.joinToString("\n"))
                        }) { Text("Share / save", color = colors.lilac) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissBackupCodesDialog() }) {
                    Text("I saved them", color = colors.amber)
                }
            }
        )
    }

    // Show new authenticator QR & verification dialog
    state.pendingTotpReconfig?.let { (secret, uri, _) ->
        if (activeDialog == SettingsDialogKind.ChangeAuthenticatorReAuth) {
            activeDialog = SettingsDialogKind.None
        }
        var verifyCode by remember(secret) { mutableStateOf("") }
        var verifyErr by remember(secret) { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { viewModel.dismissTotpReconfigDialog() }) {
            Column(
                modifier = Modifier
                    .background(colors.panel, RoundedCornerShape(18.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("New authenticator", style = MaterialTheme.typography.titleLarge, color = colors.text)
                QrCodeView(content = uri, size = 172.dp)
                Text(
                    text = TotpEngine.formatSecretForDisplay(secret),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.amber
                )
                InstrumentTextField(
                    value = verifyCode,
                    onValueChange = { verifyCode = it.filter { c -> c.isDigit() }.take(6); verifyErr = null },
                    label = "Enter 6-digit code to activate",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (!verifyErr.isNullOrBlank()) {
                    Text(verifyErr!!, style = MaterialTheme.typography.bodySmall, color = colors.coral)
                }
                AmberPrimaryButton(
                    text = "Activate authenticator",
                    enabled = verifyCode.length == 6,
                    onClick = {
                        viewModel.confirmChangeAuthenticator(verifyCode) { err -> verifyErr = err }
                    }
                )
                TextButton(onClick = { viewModel.dismissTotpReconfigDialog() }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    totpEnabled: Boolean,
    busy: Boolean,
    busyLabel: String,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String) -> Unit
) {
    val colors = LocalInstrumentColors.current
    var currentPw by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .background(colors.panel, RoundedCornerShape(18.dp))
                .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Change password", style = MaterialTheme.typography.titleLarge, color = colors.text)
            InstrumentTextField(
                value = currentPw,
                onValueChange = { currentPw = it },
                label = "Current password",
                isPassword = true
            )
            if (totpEnabled) {
                InstrumentTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it.uppercase().take(9) },
                    label = "2FA code or backup code"
                )
            }
            InstrumentTextField(
                value = newPw,
                onValueChange = { newPw = it },
                label = "New password (min 12 chars)",
                isPassword = true
            )
            InstrumentTextField(
                value = confirmPw,
                onValueChange = { confirmPw = it },
                label = "Confirm new password",
                isPassword = true
            )
            if (!error.isNullOrBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = colors.coral)
            }
            if (busy) {
                KeyDerivationBanner(label = busyLabel)
            }
            AmberPrimaryButton(
                text = "Update password",
                loading = busy,
                enabled = currentPw.isNotBlank() && newPw.length >= 12 && confirmPw.isNotBlank(),
                onClick = { onSubmit(currentPw, totpCode, newPw, confirmPw) }
            )
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                Text("Cancel", color = colors.muted)
            }
        }
    }
}

@Composable
private fun ReAuthConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    danger: Boolean,
    totpEnabled: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val colors = LocalInstrumentColors.current
    var pw by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .background(colors.panel, RoundedCornerShape(18.dp))
                .border(1.dp, if (danger) colors.coral.copy(alpha = 0.55f) else colors.line, RoundedCornerShape(18.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (danger) colors.coral else colors.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            InstrumentTextField(
                value = pw,
                onValueChange = { pw = it },
                label = "Current password",
                isPassword = true
            )
            if (totpEnabled) {
                InstrumentTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(9) },
                    label = "6-digit 2FA code or backup code"
                )
            }
            if (!error.isNullOrBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = colors.coral)
            }
            if (busy) {
                KeyDerivationBanner(label = "Verifying credentials...")
            }
            if (danger) {
                QuietOutlineButton(
                    text = confirmText,
                    danger = true,
                    enabled = !busy && pw.isNotBlank() && (!totpEnabled || code.length >= 6),
                    onClick = { onConfirm(pw, code) }
                )
            } else {
                AmberPrimaryButton(
                    text = confirmText,
                    loading = busy,
                    enabled = pw.isNotBlank() && (!totpEnabled || code.length >= 6),
                    onClick = { onConfirm(pw, code) }
                )
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                Text("Cancel", color = colors.muted)
            }
        }
    }
}

private fun formatClockTime(hour24: Int, minute: Int): String {
    val h = hour24 % 12
    val displayH = if (h == 0) 12 else h
    val amPm = if (hour24 < 12) "AM" else "PM"
    return String.format(Locale.US, "%d:%02d %s", displayH, minute, amPm)
}
