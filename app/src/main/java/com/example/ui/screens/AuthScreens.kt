package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthValidator
import com.example.config.AppConfig
import com.example.totp.TotpEngine
import com.example.ui.components.AmberPrimaryButton
import com.example.ui.components.HairlineDivider
import com.example.ui.components.InstrumentTextField
import com.example.ui.components.KeyDerivationBanner
import com.example.ui.components.QrCodeView
import com.example.ui.components.QuietOutlineButton
import com.example.ui.icons.InstrumentIcons
import com.example.ui.theme.BricolageGrotesqueFamily
import com.example.ui.theme.LocalInstrumentColors
import com.example.viewmodel.AppRoute
import com.example.viewmodel.DailyLogUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    state: DailyLogUiState,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onRestoreDriveToken: (String) -> Unit,
    onImportLocalBackup: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    var showRestoreSheet by remember { mutableStateOf(false) }
    var driveTokenInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!json.isNullOrBlank()) {
                    showRestoreSheet = false
                    onImportLocalBackup(json)
                } else {
                    onShowToast("Selected file is empty.")
                }
            } catch (_: Exception) {
                onShowToast("Could not read selected backup file.")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Neutral abstract ring emblem
                NeutralRingEmblem(modifier = Modifier.size(96.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = AppConfig.APP_NAME_PUBLIC,
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Offline-first personal log protected with local end-to-end encryption and two-factor authentication.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.hasLocalVault) {
                    AmberPrimaryButton(
                        text = "Sign in",
                        onClick = onSignIn,
                        icon = InstrumentIcons.Lock,
                        modifier = Modifier.testTag("welcome_sign_in_button")
                    )
                    QuietOutlineButton(
                        text = "Create account",
                        onClick = onCreateAccount,
                        modifier = Modifier.testTag("welcome_create_account_button")
                    )
                } else {
                    AmberPrimaryButton(
                        text = "Create account",
                        onClick = onCreateAccount,
                        icon = InstrumentIcons.Shield,
                        modifier = Modifier.testTag("welcome_create_account_button")
                    )
                    QuietOutlineButton(
                        text = "Sign in",
                        onClick = onSignIn,
                        icon = InstrumentIcons.Lock,
                        modifier = Modifier.testTag("welcome_sign_in_button")
                    )
                }

                QuietOutlineButton(
                    text = "Restore from Google Drive",
                    onClick = { showRestoreSheet = true },
                    icon = InstrumentIcons.Cloud,
                    modifier = Modifier.testTag("welcome_restore_drive_button")
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All records stay encrypted on this device. No cloud account is required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showRestoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRestoreSheet = false },
            containerColor = colors.panel,
            contentColor = colors.text
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Restore encrypted vault",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text
                )
                Text(
                    text = "Download ${AppConfig.DRIVE_BACKUP_FILENAME} from your private Google Drive appDataFolder, or import a saved encrypted backup file. You will then sign in with your password and 2FA (or recovery key).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                if (AppConfig.DRIVE_OAUTH_CLIENT_ID.isBlank()) {
                    Text(
                        text = "Google OAuth Client ID is not configured in AppConfig.kt yet. You can paste a temporary drive.appdata OAuth access token below or restore from a local encrypted file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.amber
                    )
                }
                InstrumentTextField(
                    value = driveTokenInput,
                    onValueChange = { driveTokenInput = it },
                    label = "Google Drive OAuth access token (drive.appdata)",
                    placeholder = "ya29...."
                )
                AmberPrimaryButton(
                    text = "Download from Google Drive",
                    loading = state.isDriveSyncing,
                    enabled = driveTokenInput.isNotBlank(),
                    onClick = {
                        onRestoreDriveToken(driveTokenInput)
                        showRestoreSheet = false
                    },
                    modifier = Modifier.testTag("restore_drive_submit_button")
                )
                HairlineDivider()
                QuietOutlineButton(
                    text = "Import local ${AppConfig.DRIVE_BACKUP_FILENAME} file",
                    icon = InstrumentIcons.Download,
                    onClick = { filePicker.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.testTag("restore_local_file_button")
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun NeutralRingEmblem(modifier: Modifier = Modifier) {
    val colors = LocalInstrumentColors.current
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.44f
        val tickCount = 36
        for (i in 0 until tickCount) {
            val angle = (i.toDouble() / tickCount) * 2.0 * PI - PI / 2.0
            val len = if (i % 3 == 0) size.minDimension * 0.11f else size.minDimension * 0.065f
            val inner = r - len
            val isAmber = i < 24
            drawLine(
                color = if (isAmber) colors.dialAmber else colors.line,
                start = Offset(center.x + (inner * cos(angle)).toFloat(), center.y + (inner * sin(angle)).toFloat()),
                end = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat()),
                strokeWidth = if (isAmber) 2.2.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        val mintAngle = (24.0 / tickCount) * 2.0 * PI - PI / 2.0
        drawCircle(
            color = colors.mint,
            radius = 3.5.dp.toPx(),
            center = Offset(
                center.x + ((r + 5.dp.toPx()) * cos(mintAngle)).toFloat(),
                center.y + ((r + 5.dp.toPx()) * sin(mintAngle)).toFloat()
            )
        )
    }
}

@Composable
fun SignUpScreen(
    state: DailyLogUiState,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onContinue: (String, String, String, String, String, String) -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalInstrumentColors.current
    var idType by remember { mutableStateOf("email") }
    var email by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(AuthValidator.COUNTRY_CODES.first()) }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var nationalPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val activeIdentifier = if (idType == "email") {
        email.trim()
    } else {
        AuthValidator.normalizePhoneToE164(selectedCountry.dialCode, nationalPhone)
    }
    val strength = remember(password, activeIdentifier) {
        AuthValidator.evaluatePassword(password, activeIdentifier)
    }

    AuthScaffold(
        title = "Create account",
        subtitle = "Set up your offline encrypted ${AppConfig.APP_NAME_PUBLIC} vault.",
        onBack = onBack
    ) {
        // Segmented toggle: Email | Phone
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(12.dp))
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("email" to "Email address", "phone" to "Phone number").forEach { (key, label) ->
                val active = idType == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            if (active) colors.panelElevated else colors.panel,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable {
                            idType = key
                            onClearError()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) colors.amber else colors.muted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (idType == "email") {
            InstrumentTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                label = "Email address",
                placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.testTag("signup_email_input")
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .height(56.dp)
                            .background(colors.panel, RoundedCornerShape(12.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .clickable { countryMenuOpen = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedCountry.iso} ${selectedCountry.dialCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text
                        )
                    }
                    DropdownMenu(
                        expanded = countryMenuOpen,
                        onDismissRequest = { countryMenuOpen = false }
                    ) {
                        AuthValidator.COUNTRY_CODES.forEach { country ->
                            DropdownMenuItem(
                                text = { Text("${country.name} (${country.dialCode})") },
                                onClick = {
                                    selectedCountry = country
                                    countryMenuOpen = false
                                }
                            )
                        }
                    }
                }
                InstrumentTextField(
                    value = nationalPhone,
                    onValueChange = { nationalPhone = it.filter { ch -> ch.isDigit() || ch == ' ' || ch == '-' }; onClearError() },
                    label = "Phone number",
                    placeholder = "555 019 2834",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f).testTag("signup_phone_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        InstrumentTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = "Password (minimum 12 characters)",
            isPassword = true,
            passwordVisible = showPassword,
            onTogglePassword = { showPassword = !showPassword },
            isError = password.isNotEmpty() && !strength.isValid,
            supportingText = strength.error,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.testTag("signup_password_input")
        )

        // Live local password strength meter
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (seg in 1..4) {
                val activeColor = when (strength.score) {
                    1 -> colors.coral
                    2 -> colors.amber
                    3 -> colors.lilac
                    4 -> colors.mint
                    else -> colors.line
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (strength.score >= seg) activeColor else colors.line,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Strength: ${strength.label}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted
        )

        Spacer(modifier = Modifier.height(14.dp))

        InstrumentTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; onClearError() },
            label = "Confirm password",
            isPassword = true,
            passwordVisible = showConfirm,
            onTogglePassword = { showConfirm = !showConfirm },
            isError = confirmPassword.isNotEmpty() && confirmPassword != password,
            supportingText = if (confirmPassword.isNotEmpty() && confirmPassword != password) "Passwords do not match." else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.testTag("signup_confirm_password_input")
        )

        if (!state.authError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.authError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.coral
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        AmberPrimaryButton(
            text = "Generate recovery key",
            onClick = {
                onContinue(idType, email, selectedCountry.dialCode, nationalPhone, password, confirmPassword)
            },
            modifier = Modifier.testTag("signup_continue_button")
        )
    }
}

@Composable
fun RecoveryKeyScreen(
    route: AppRoute.ShowRecoveryKey,
    state: DailyLogUiState,
    onBack: () -> Unit,
    onConfirm: (Map<Int, String>) -> Unit,
    onShowToast: (String) -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    val entered = remember(route.recoveryKey) { mutableStateMapOf<Int, String>() }

    AuthScaffold(
        title = "Save your recovery key",
        subtitle = "Shown once. Write it down or print it now.",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(16.dp))
                .border(1.dp, colors.amber.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = route.recoveryKey,
                fontFamily = BricolageGrotesqueFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = 0.8.sp,
                color = colors.amber,
                modifier = Modifier.testTag("recovery_key_text")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietOutlineButton(
                    text = "Copy key",
                    icon = InstrumentIcons.Copy,
                    onClick = {
                        copyToClipboard(context, "Daily Log recovery key", route.recoveryKey)
                        onShowToast("Recovery key copied to clipboard.")
                    },
                    modifier = Modifier.weight(1f).testTag("copy_recovery_key_button")
                )
                QuietOutlineButton(
                    text = "Print / save",
                    icon = InstrumentIcons.Download,
                    onClick = {
                        sharePlainText(
                            context,
                            "${AppConfig.APP_NAME_PUBLIC} Recovery Key",
                            "${AppConfig.APP_NAME_PUBLIC} Recovery Key\n\n${route.recoveryKey}\n\nStore this somewhere safe offline. If both your password and recovery key are lost, encrypted data cannot be recovered."
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "If both your password and recovery key are lost, your encrypted ${AppConfig.APP_NAME_PUBLIC} vault cannot be recovered by anyone.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.coral
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Confirm you saved it by entering these 3 groups:",
            style = MaterialTheme.typography.titleSmall,
            color = colors.text
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            route.confirmGroupIndices.forEach { groupIdx ->
                InstrumentTextField(
                    value = entered[groupIdx].orEmpty(),
                    onValueChange = { entered[groupIdx] = it.uppercase().take(4) },
                    label = "Group ${groupIdx + 1}",
                    placeholder = "XXXX",
                    modifier = Modifier.weight(1f).testTag("recovery_confirm_${groupIdx + 1}")
                )
            }
        }

        if (!state.authError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.authError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.coral
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        AmberPrimaryButton(
            text = "Continue to 2FA setup",
            onClick = { onConfirm(entered.toMap()) },
            modifier = Modifier.testTag("recovery_confirm_button")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Setup2FaScreen(
    route: AppRoute.Setup2Fa,
    state: DailyLogUiState,
    onBack: () -> Unit,
    onVerifyAndFinish: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    var totpInput by remember { mutableStateOf("") }

    AuthScaffold(
        title = "Set up two-factor authentication",
        subtitle = "Works completely offline with any standard TOTP authenticator app.",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QrCodeView(content = route.otpAuthUri, size = 192.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Manual setup key",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = TotpEngine.formatSecretForDisplay(route.secret),
                style = MaterialTheme.typography.titleSmall,
                color = colors.amber,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    copyToClipboard(context, "Daily Log 2FA key", route.secret)
                    onShowToast("Setup key copied.")
                }
            ) {
                Icon(
                    imageVector = InstrumentIcons.Copy,
                    contentDescription = null,
                    tint = colors.lilac,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy setup key", color = colors.lilac)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 10 Single-use backup codes shown once
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(16.dp))
                .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "10 single-use backup codes (shown once)",
                style = MaterialTheme.typography.titleSmall,
                color = colors.text
            )
            Text(
                text = "Save these offline in case you lose access to your authenticator app. Stored hashed inside your encrypted vault.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                route.backupCodes.forEach { code ->
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text,
                        modifier = Modifier
                            .background(colors.panelElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietOutlineButton(
                    text = "Copy codes",
                    onClick = {
                        copyToClipboard(context, "Daily Log backup codes", route.backupCodes.joinToString("\n"))
                        onShowToast("Backup codes copied.")
                    },
                    modifier = Modifier.weight(1f)
                )
                QuietOutlineButton(
                    text = "Save / share",
                    onClick = {
                        sharePlainText(
                            context,
                            "${AppConfig.APP_NAME_PUBLIC} Backup Codes",
                            "${AppConfig.APP_NAME_PUBLIC} Single-Use Backup Codes\n\n" + route.backupCodes.joinToString("\n")
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        InstrumentTextField(
            value = totpInput,
            onValueChange = { totpInput = it.filter { ch -> ch.isDigit() }.take(6) },
            label = "Enter 6-digit authenticator code",
            placeholder = "000000",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.testTag("setup_2fa_code_input")
        )

        if (!state.authError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.authError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.coral
            )
        }

        if (state.isCryptoBusy) {
            Spacer(modifier = Modifier.height(12.dp))
            KeyDerivationBanner(label = state.cryptoBusyLabel)
        }

        Spacer(modifier = Modifier.height(18.dp))

        AmberPrimaryButton(
            text = "Verify and create vault",
            loading = state.isCryptoBusy,
            enabled = totpInput.length == 6,
            onClick = { onVerifyAndFinish(totpInput) },
            modifier = Modifier.testTag("setup_2fa_verify_button")
        )
    }
}

@Composable
internal fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalInstrumentColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = InstrumentIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.text
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = AppConfig.APP_NAME_PUBLIC,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.muted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
            Spacer(modifier = Modifier.height(22.dp))
            content()
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun sharePlainText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}
