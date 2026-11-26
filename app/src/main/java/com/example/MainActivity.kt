package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crypto.BiometricKeyManager
import com.example.model.ComputedStats
import com.example.model.QuitPlan
import com.example.ui.components.InstrumentBottomBar
import com.example.ui.components.InstrumentNavigationRail
import com.example.ui.components.MainTab
import com.example.ui.motion.InstrumentMotion
import com.example.ui.motion.InstrumentToast
import com.example.ui.screens.BodyScreen
import com.example.ui.screens.CravingSosScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.QuickUnlockScreen
import com.example.ui.screens.RecoveryKeyScreen
import com.example.ui.screens.ResetPasswordScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.Setup2FaScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.SignUpScreen
import com.example.ui.screens.SlipHandlingSheet
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.DailyLogTheme
import com.example.ui.theme.LocalInstrumentColors
import com.example.ui.theme.LocalReduceMotion
import com.example.viewmodel.AppRoute
import com.example.viewmodel.DailyLogViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: DailyLogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyLogApp(activity = this, viewModel = viewModel)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }
}

@Composable
fun DailyLogApp(activity: FragmentActivity, viewModel: DailyLogViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemReduceMotion = remember(context) { InstrumentMotion.isSystemReducedMotion(context) }
    val effectiveReduceMotion = state.reduceMotion || systemReduceMotion
    val biometricAvailability = remember(context, state.route) {
        BiometricKeyManager.checkAvailability(context)
    }
    val canUseFingerprint = state.biometricEnabled &&
        state.hasLocalVault &&
        biometricAvailability == BiometricKeyManager.Availability.Available

    val triggerEnableBiometric: (Boolean) -> Unit = { enable ->
        if (!enable) {
            viewModel.disableBiometricUnlock()
        } else {
            val cipher = viewModel.prepareBiometricEnableCipher()
            if (cipher != null) {
                BiometricKeyManager.authenticate(
                    activity = activity,
                    title = BiometricKeyManager.defaultPromptTitle(),
                    subtitle = "Confirm your fingerprint to enable biometric unlock",
                    negativeButtonText = "Cancel",
                    cipher = cipher,
                    onSuccess = { authed -> viewModel.completeBiometricEnable(authed) },
                    onError = { err -> viewModel.showToast(err, isError = true) }
                )
            }
        }
    }

    val triggerUnlockWithBiometric: (Boolean) -> Unit = { isQuickUnlock ->
        val cipher = viewModel.prepareBiometricUnlockCipher()
        if (cipher != null) {
            BiometricKeyManager.authenticate(
                activity = activity,
                title = BiometricKeyManager.defaultPromptTitle(),
                subtitle = if (isQuickUnlock) "Unlock your encrypted vault" else "Sign in to your encrypted vault",
                negativeButtonText = "Use password",
                cipher = cipher,
                onSuccess = { authed -> viewModel.unlockWithBiometricCipher(authed, isQuickUnlock) },
                onError = { err -> viewModel.showToast(err, isError = true) }
            )
        }
    }

    var showCravingSos by remember { mutableStateOf(false) }
    var showEditQuitPlan by remember { mutableStateOf(false) }
    var pendingSlipTriggerAfterSos by remember { mutableStateOf<String?>(null) }

    DailyLogTheme(
        darkTheme = state.darkTheme,
        reduceMotion = effectiveReduceMotion
    ) {
        val colors = LocalInstrumentColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            when (val route = state.route) {
                is AppRoute.Welcome -> {
                    WelcomeScreen(
                        state = state,
                        onSignIn = { viewModel.navigateToSignIn() },
                        onCreateAccount = { viewModel.navigateToSignUp() },
                        onRestoreDriveToken = { viewModel.restoreFromDriveToken(it) },
                        onImportLocalBackup = { viewModel.importEncryptedVaultJson(it) },
                        onShowToast = { viewModel.showToast(it) }
                    )
                }

                is AppRoute.SignUp -> {
                    SignUpScreen(
                        state = state,
                        onBack = { viewModel.navigateToWelcome() },
                        onClearError = { viewModel.clearAuthError() },
                        onContinue = { type, email, dial, phone, pw, conf ->
                            viewModel.beginSignUp(type, email, dial, phone, pw, conf)
                        }
                    )
                }

                is AppRoute.ShowRecoveryKey -> {
                    RecoveryKeyScreen(
                        route = route,
                        state = state,
                        onBack = { viewModel.navigateToSignUp() },
                        onConfirm = { viewModel.confirmRecoveryKeyAndFinishSignUp(it) },
                        onShowToast = { viewModel.showToast(it) }
                    )
                }

                is AppRoute.Setup2Fa -> {
                    // Legacy/optional route safeguard
                    Setup2FaScreen(
                        route = route,
                        state = state,
                        onBack = { viewModel.navigateToSignUp() },
                        onVerifyAndFinish = { },
                        onShowToast = { viewModel.showToast(it) }
                    )
                }

                is AppRoute.SignIn -> {
                    SignInScreen(
                        route = route,
                        state = state,
                        canUseFingerprint = canUseFingerprint,
                        onBack = { viewModel.navigateToWelcome() },
                        onPasswordSubmit = { id, pw -> viewModel.signInPasswordStep(id, pw) },
                        onFingerprintSignIn = { triggerUnlockWithBiometric(false) },
                        onTwoFactorSubmit = { code -> viewModel.signInTwoFactorStep(code) },
                        onForgotPassword = { viewModel.navigateToResetPassword() },
                        onCreateAccount = { viewModel.navigateToSignUp() },
                        onClearError = { viewModel.clearAuthError() }
                    )
                }

                is AppRoute.QuickUnlock -> {
                    QuickUnlockScreen(
                        state = state,
                        canUseFingerprint = canUseFingerprint,
                        onUnlock = { pw -> viewModel.quickUnlockWithPassword(pw) },
                        onFingerprintUnlock = { triggerUnlockWithBiometric(true) },
                        onFullSignIn = { viewModel.navigateToSignIn() },
                        onForgotPassword = { viewModel.navigateToResetPassword() },
                        onClearError = { viewModel.clearAuthError() }
                    )
                }

                is AppRoute.ResetPassword -> {
                    ResetPasswordScreen(
                        route = route,
                        state = state,
                        onBack = { viewModel.navigateToSignIn() },
                        onVerifyRecoveryKey = { id, key -> viewModel.resetVerifyRecoveryKey(id, key) },
                        onVerify2Fa = { code -> viewModel.resetVerify2Fa(code) },
                        onCommitNewPassword = { pw, conf -> viewModel.resetCommitNewPassword(pw, conf) },
                        onClearError = { viewModel.clearAuthError() }
                    )
                }

                is AppRoute.Onboarding -> {
                    OnboardingScreen(
                        initialPlan = state.payload?.quitPlan ?: QuitPlan(),
                        isEditingExisting = false,
                        biometricAvailable = biometricAvailability == BiometricKeyManager.Availability.Available,
                        biometricEnabled = state.biometricEnabled,
                        onToggleBiometric = triggerEnableBiometric,
                        onSavePlan = { plan -> viewModel.saveQuitPlan(plan, fromOnboarding = true) }
                    )
                }

                is AppRoute.Dashboard -> {
                    val payload = state.payload
                    if (payload != null) {
                        when {
                            showCravingSos -> {
                                CravingSosScreen(
                                    userReason = payload.quitPlan.reason,
                                    onClose = { showCravingSos = false },
                                    onResisted = { intensity, trigger, duration ->
                                        showCravingSos = false
                                        viewModel.logCravingSession(intensity, trigger, duration, resisted = true)
                                    },
                                    onUsed = { intensity, trigger, duration ->
                                        showCravingSos = false
                                        viewModel.logCravingSession(intensity, trigger, duration, resisted = false)
                                        pendingSlipTriggerAfterSos = trigger
                                    }
                                )
                            }

                            showEditQuitPlan -> {
                                OnboardingScreen(
                                    initialPlan = payload.quitPlan,
                                    isEditingExisting = true,
                                    onCancelEdit = { showEditQuitPlan = false },
                                    onSavePlan = { updated ->
                                        showEditQuitPlan = false
                                        viewModel.saveQuitPlan(updated, fromOnboarding = false)
                                    }
                                )
                            }

                            else -> {
                                DashboardShell(
                                    state = state,
                                    viewModel = viewModel,
                                    biometricAvailability = biometricAvailability,
                                    onToggleBiometric = triggerEnableBiometric,
                                    onOpenCravingSos = { showCravingSos = true },
                                    onEditQuitPlan = { showEditQuitPlan = true }
                                )
                            }
                        }

                        pendingSlipTriggerAfterSos?.let { trigger ->
                            SlipHandlingSheet(
                                initialTrigger = trigger,
                                onDismiss = { pendingSlipTriggerAfterSos = null },
                                onConfirmSlip = { chosenTrigger, note ->
                                    pendingSlipTriggerAfterSos = null
                                    viewModel.logSlip(chosenTrigger, note)
                                }
                            )
                        }
                    }
                }
            }

            // Floating animated toast pill above bottom navigation
            InstrumentToast(
                message = state.toastMessage,
                isError = state.toastIsError,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (state.route is AppRoute.Dashboard && !showCravingSos) 76.dp else 20.dp)
            )
        }
    }
}

@Composable
private fun DashboardShell(
    state: com.example.viewmodel.DailyLogUiState,
    viewModel: DailyLogViewModel,
    biometricAvailability: BiometricKeyManager.Availability,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenCravingSos: () -> Unit,
    onEditQuitPlan: () -> Unit
) {
    val colors = LocalInstrumentColors.current
    val reduceMotion = LocalReduceMotion.current
    val slidePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val payload = state.payload ?: return
    val stats = state.stats ?: ComputedStats(
        hasAnyData = false,
        cravingSeries14d = emptyList(),
        moodSeries14d = emptyList(),
        sleepSeries14d = emptyList(),
        cravingTrendText = "",
        moodTrendText = "",
        sleepTrendText = "",
        cravingsResisted = 0,
        slipsCount = 0,
        topTriggers = emptyList(),
        peakCravingHour = null,
        peakCravingLabel = null
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        val tabContent: @Composable (Modifier) -> Unit = { contentModifier ->
            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    } else {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val enterOffset = if (forward) slidePx else -slidePx
                        val exitOffset = if (forward) -slidePx else slidePx
                        (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { enterOffset }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { exitOffset })
                    }
                },
                label = "dashboardTabTransition",
                modifier = contentModifier
            ) { tab ->
                when (tab) {
                    MainTab.Today -> TodayScreen(
                        payload = payload,
                        hasPlayedEntrance = state.hasPlayedEntranceThisSession,
                        pulsingMilestoneIndex = state.pulsingMilestoneIndex,
                        newlyReachedMilestone = state.newlyReachedMilestone,
                        onMarkEntrancePlayed = { viewModel.markEntrancePlayed() },
                        onOpenCravingSos = onOpenCravingSos,
                        onLockNow = { viewModel.lockNow(forceFull2fa = false) },
                        onLogCheckIn = { mood, sleep, craving, used, note ->
                            viewModel.logDailyCheckIn(mood, sleep, craving, used, note)
                        },
                        onLogSlip = { trigger, note -> viewModel.logSlip(trigger, note) },
                        onCheckMilestoneTick = { viewModel.checkMilestoneTick() },
                        onDismissMilestone = { viewModel.dismissMilestoneSheet() }
                    )

                    MainTab.Stats -> StatsScreen(stats = stats)

                    MainTab.Body -> BodyScreen(payload = payload)

                    MainTab.Settings -> SettingsScreen(
                        state = state,
                        viewModel = viewModel,
                        biometricAvailability = biometricAvailability,
                        onToggleBiometric = onToggleBiometric,
                        onEditQuitPlan = onEditQuitPlan
                    )
                }
            }
        }

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize().background(colors.background)) {
                InstrumentNavigationRail(
                    selectedTab = state.selectedTab,
                    onSelect = { viewModel.selectTab(it) }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    tabContent(Modifier.fillMaxSize())
                }
            }
        } else {
            Scaffold(
                containerColor = colors.background,
                bottomBar = {
                    InstrumentBottomBar(
                        selectedTab = state.selectedTab,
                        onSelect = { viewModel.selectTab(it) }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    tabContent(Modifier.fillMaxSize())
                }
            }
        }
    }
}
