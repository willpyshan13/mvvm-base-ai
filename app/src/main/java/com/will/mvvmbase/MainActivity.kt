package com.will.mvvmbase

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.will.mvvmbase.core.apm.AppApm
import com.will.mvvmbase.core.designsystem.theme.MvvmBaseTheme
import com.will.mvvmbase.core.designsystem.theme.AppColorPalette
import com.will.mvvmbase.core.media.AppVideoCacheSettings
import com.will.mvvmbase.core.navigation.AuthRequiredNavKey
import com.will.mvvmbase.core.navigation.FeatureEntryBuilder
import com.will.mvvmbase.core.navigation.Navigator
import com.will.mvvmbase.feature.auth.LoginKey
import com.will.mvvmbase.feature.auth.domain.AuthRepository
import com.will.mvvmbase.feature.auth.domain.AuthState
import com.will.mvvmbase.feature.settings.domain.SettingsRepository
import com.will.mvvmbase.feature.home.TasksKey
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var entryBuilders: Set<@JvmSuppressWildcards FeatureEntryBuilder>
    private val pendingActivityIntent = MutableStateFlow<Intent?>(null)
    private val requireLogin = MutableStateFlow(false)
    private val incomingLink = MutableStateFlow<android.net.Uri?>(null)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingLink.value = if (savedInstanceState == null) intent.data else null
        pendingActivityIntent.value = savedInstanceState?.getParcelable<Intent>(
            AuthActivityGate.STATE_PENDING_ACTIVITY_INTENT,
        ) ?: intent.getParcelableExtra<Intent>(AuthActivityGate.EXTRA_PENDING_ACTIVITY_INTENT)
        requireLogin.value = savedInstanceState?.getBoolean(
            AuthActivityGate.STATE_REQUIRE_LOGIN,
        ) ?: intent.getBooleanExtra(AuthActivityGate.EXTRA_REQUIRE_LOGIN, false)
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = com.will.mvvmbase.feature.settings.domain.ThemeMode.System)
            val themePalette by settingsRepository.themePalette.collectAsStateWithLifecycle(
                initialValue = com.will.mvvmbase.feature.settings.domain.ThemePalette.Blue)
            val darkTheme = when (themeMode) {
                com.will.mvvmbase.feature.settings.domain.ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                com.will.mvvmbase.feature.settings.domain.ThemeMode.Dark -> true
                com.will.mvvmbase.feature.settings.domain.ThemeMode.Light -> false
            }
            androidx.compose.runtime.SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            val videoCacheSizeMb by settingsRepository.videoCacheSizeMb.collectAsStateWithLifecycle(
                initialValue = SettingsRepository.DEFAULT_VIDEO_CACHE_SIZE_MB,
            )
            val authState by authRepository.state.collectAsStateWithLifecycle()
            val activityTarget by pendingActivityIntent.collectAsStateWithLifecycle()
            val loginRequired by requireLogin.collectAsStateWithLifecycle()
            LaunchedEffect(videoCacheSizeMb) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppVideoCacheSettings.configure(applicationContext, videoCacheSizeMb) }
            }
            MvvmBaseTheme(darkTheme = darkTheme, palette = when (themePalette) {
                com.will.mvvmbase.feature.settings.domain.ThemePalette.Blue -> AppColorPalette.Blue
                com.will.mvvmbase.feature.settings.domain.ThemePalette.Green -> AppColorPalette.Green
                com.will.mvvmbase.feature.settings.domain.ThemePalette.Purple -> AppColorPalette.Purple
            }) {
                var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
                val tasksStack = rememberNavBackStack(TasksKey)
                val toolsStack = rememberNavBackStack(com.will.mvvmbase.feature.toolbox.ToolboxKey)
                val settingsStack = rememberNavBackStack(com.will.mvvmbase.feature.settings.SettingsKey)
                val inspectionsStack = rememberNavBackStack(com.will.mvvmbase.feature.inspection.InspectionsKey)
                val backStack = when (selectedTab) { 1 -> toolsStack; 2 -> settingsStack; 3 -> inspectionsStack; else -> tasksStack }
                val currentScreen = when (backStack.lastOrNull()) {
                    TasksKey -> "home.tasks"
                    com.will.mvvmbase.feature.home.SummaryKey -> "home.summary"
                    LoginKey -> "auth.login"
                    com.will.mvvmbase.feature.toolbox.ToolboxKey -> "toolbox.home"
                    com.will.mvvmbase.feature.toolbox.ResultPickerKey -> "toolbox.result_picker"
                    com.will.mvvmbase.feature.settings.SettingsKey -> "settings"
                    com.will.mvvmbase.feature.status.StatusKey -> "status"
                    com.will.mvvmbase.feature.feed.FeedKey -> "feed"
                    com.will.mvvmbase.feature.inspection.InspectionsKey -> "inspection.list"
                    is com.will.mvvmbase.feature.inspection.InspectionEditorKey -> "inspection.editor"
                    com.will.mvvmbase.feature.media.VideoPlayerKey -> "media.video_player"
                    else -> "other"
                }
                LaunchedEffect(selectedTab, currentScreen) {
                    AppApm.recordScreenView(currentScreen)
                }
                androidx.activity.compose.BackHandler(selectedTab != 0 && backStack.size == 1) { selectedTab = 0 }
                var savedResults by androidx.compose.runtime.saveable.rememberSaveable {
                    mutableStateOf(hashMapOf<String, String>())
                }
                val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                val resultFlows = remember { mutableMapOf<String, MutableStateFlow<String?>>() }
                val incoming by incomingLink.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    androidx.compose.runtime.withFrameNanos { }
                    com.will.mvvmbase.core.platform.AppDiagnostics.firstFrame()
                }
                var wasAuthenticated by remember(authRepository) {
                    mutableStateOf(authState is AuthState.SignedIn)
                }
                val coroutineScope = rememberCoroutineScope()

                fun completeLogin() {
                    var loginIndex = backStack.indexOf(LoginKey)
                    while (loginIndex >= 0) {
                        backStack.removeAt(loginIndex)
                        loginIndex = backStack.indexOf(LoginKey)
                    }
                    val externalActivity = pendingActivityIntent.value
                    if (externalActivity != null) {
                        pendingActivityIntent.value = null
                        requireLogin.value = false
                        startActivity(externalActivity)
                        return
                    }
                    requireLogin.value = false
                    if (backStack.isEmpty()) backStack.add(TasksKey)
                }

                val currentAuth = androidx.compose.runtime.rememberUpdatedState(authState)
                val authentication = remember { MutableStateFlow(authState is AuthState.SignedIn) }
                androidx.compose.runtime.SideEffect { authentication.value = authState is AuthState.SignedIn }
                val navigator = remember(backStack, authRepository) {
                    object : Navigator {
                        override val authentication = authentication
                        override val isAuthenticated: Boolean
                            get() = currentAuth.value is AuthState.SignedIn

                        override fun navigate(destination: NavKey) {
                            if (destination !is AuthRequiredNavKey) {
                                backStack.add(destination)
                                return
                            }

                            when (currentAuth.value) {
                                AuthState.Loading -> {
                                    if (backStack.lastOrNull() != destination) backStack.add(destination)
                                }
                                AuthState.SignedOut -> {
                                    if (backStack.lastOrNull() != LoginKey && backStack.lastOrNull() != destination) {
                                        backStack.add(destination)
                                    }
                                    if (backStack.lastOrNull() != LoginKey) backStack.add(LoginKey)
                                }
                                AuthState.SignedIn -> {
                                    if (backStack.lastOrNull() != destination) backStack.add(destination)
                                }
                            }
                        }

                        override fun resultPayload(key: String): kotlinx.coroutines.flow.StateFlow<String?> =
                            resultFlows.getOrPut(key) { MutableStateFlow(savedResults[key]) }

                        override fun consumeResult(key: String) {
                            savedResults = HashMap(savedResults).apply { remove(key) }
                            resultFlows[key]?.value = null
                        }

                        override fun returnResultPayload(key: String, value: String) {
                            savedResults = HashMap(savedResults).apply { put(key, value) }
                            resultFlows.getOrPut(key) { MutableStateFlow(null) }.value = value
                            back()
                        }

                        override fun back() {
                            if (backStack.lastOrNull() == LoginKey) {
                                pendingActivityIntent.value = null
                                requireLogin.value = false
                                if (currentAuth.value !is AuthState.SignedIn) {
                                    while (backStack.getOrNull(backStack.lastIndex - 1) is AuthRequiredNavKey) {
                                        backStack.removeAt(backStack.lastIndex - 1)
                                    }
                                }
                            }
                            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        }

                        override fun login() {
                            if (backStack.lastOrNull() != LoginKey) backStack.add(LoginKey)
                        }

                        override fun logout() {
                            pendingActivityIntent.value = null
                            requireLogin.value = false
                            tasksStack.clear(); tasksStack.add(TasksKey)
                            toolsStack.clear(); toolsStack.add(com.will.mvvmbase.feature.toolbox.ToolboxKey)
                            settingsStack.clear(); settingsStack.add(com.will.mvvmbase.feature.settings.SettingsKey)
                            inspectionsStack.clear(); inspectionsStack.add(com.will.mvvmbase.feature.inspection.InspectionsKey)
                            selectedTab = 0
                            savedResults = hashMapOf()
                            resultFlows.values.forEach { it.value = null }
                            coroutineScope.launch {
                                runCatching { authRepository.logout() }
                                    .onFailure(com.will.mvvmbase.core.apm.AppApm::recordException)
                            }
                        }

                        override fun onLoginSuccess() = completeLogin()
                    }
                }

                LaunchedEffect(incoming, authState, selectedTab) {
                    if (incoming == null || authState is AuthState.Loading) return@LaunchedEffect
                    incoming?.let { uri ->
                        val destination: NavKey? = if (uri.scheme == "wbase" && uri.host == "open") {
                            when (uri.path) {
                                "/toolbox" -> com.will.mvvmbase.feature.toolbox.ToolboxKey
                                "/video" -> com.will.mvvmbase.feature.media.VideoPlayerKey
                                "/feed" -> com.will.mvvmbase.feature.feed.FeedKey
                                "/inspection" -> com.will.mvvmbase.feature.inspection.InspectionsKey
                                "/settings" -> com.will.mvvmbase.feature.settings.SettingsKey
                                "/summary" -> com.will.mvvmbase.feature.home.SummaryKey
                                else -> null
                            }
                        } else null
                        if (destination != null) {
                            // Switch stack first; navigate after the selected stack and auth gate agree.
                            val destinationTab = if (destination == com.will.mvvmbase.feature.inspection.InspectionsKey) 3 else 1
                            if (selectedTab != destinationTab) { selectedTab = destinationTab; return@LaunchedEffect }
                            if (backStack.lastOrNull() != destination) navigator.navigate(destination)
                        }
                        incomingLink.value = null
                    }
                }
                LaunchedEffect(authState, activityTarget, loginRequired, selectedTab, backStack.lastOrNull()) {
                    when (authState) {
                        AuthState.Loading -> Unit
                        AuthState.SignedIn -> {
                            wasAuthenticated = true
                            if (activityTarget != null || backStack.lastOrNull() == LoginKey) completeLogin()
                        }
                        AuthState.SignedOut -> {
                            val protectedDestination = when (backStack.lastOrNull()) {
                                is AuthRequiredNavKey -> backStack.lastOrNull()
                                LoginKey -> backStack.getOrNull(backStack.lastIndex - 1)
                                    ?.takeIf { it is AuthRequiredNavKey }
                                else -> null
                            }
                            listOf(tasksStack, toolsStack, settingsStack, inspectionsStack).filter { it !== backStack }.forEach { stack ->
                                stack.removeAll { it is AuthRequiredNavKey || it == LoginKey }
                            }
                            if (wasAuthenticated || protectedDestination != null) {
                                wasAuthenticated = false
                                backStack.clear()
                                backStack.add(when (selectedTab) {
                                    1 -> com.will.mvvmbase.feature.toolbox.ToolboxKey
                                    2 -> com.will.mvvmbase.feature.settings.SettingsKey
                                    3 -> com.will.mvvmbase.feature.inspection.InspectionsKey
                                    else -> TasksKey
                                })
                                if (protectedDestination != null) backStack.add(protectedDestination)
                            }
                            if ((protectedDestination != null || activityTarget != null || loginRequired) &&
                                backStack.lastOrNull() != LoginKey
                            ) {
                                backStack.add(LoginKey)
                            }
                        }
                    }
                }

                val protectedRoutePendingRedirect =
                    authState is AuthState.SignedOut && backStack.lastOrNull() is AuthRequiredNavKey
                if (authState is AuthState.Loading || protectedRoutePendingRedirect) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AppNavigationShell(selectedTab, { selectedTab = it }) {
                    tabStateHolder.SaveableStateProvider(selectedTab) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size == 1 && selectedTab != 0) selectedTab = 0 else navigator.back() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            entryBuilders.forEach { it.register(this, navigator) }
                        },
                    )
                    }
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        com.will.mvvmbase.core.media.AppPictureInPicture.update(inPictureInPictureMode)
    }

    internal fun openDeepLink(uri: android.net.Uri?) { incomingLink.value = uri }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDeepLink(intent.data)
        pendingActivityIntent.value = intent.getParcelableExtra<Intent>(
            AuthActivityGate.EXTRA_PENDING_ACTIVITY_INTENT,
        )
        requireLogin.value = intent.getBooleanExtra(AuthActivityGate.EXTRA_REQUIRE_LOGIN, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(AuthActivityGate.STATE_PENDING_ACTIVITY_INTENT, pendingActivityIntent.value)
        outState.putBoolean(AuthActivityGate.STATE_REQUIRE_LOGIN, requireLogin.value)
        super.onSaveInstanceState(outState)
    }
}
