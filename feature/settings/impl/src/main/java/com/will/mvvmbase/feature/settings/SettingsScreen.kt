package com.will.mvvmbase.feature.settings

import com.will.mvvmbase.feature.settings.R

import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.will.mvvmbase.core.designsystem.components.AppConfirmDialog
import com.will.mvvmbase.core.designsystem.components.AppInfoDialog
import com.will.mvvmbase.core.designsystem.components.AppSnackbarHost
import com.will.mvvmbase.feature.settings.domain.SettingsRepository
import com.will.mvvmbase.feature.settings.domain.ThemeMode
import com.will.mvvmbase.feature.settings.domain.ThemePalette
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun SettingsRoute(
    isAuthenticated: StateFlow<Boolean>,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val authenticated by isAuthenticated.collectAsStateWithLifecycle()
    val themePalette by viewModel.themePalette.collectAsStateWithLifecycle()
    val videoCacheSizeMb by viewModel.videoCacheSizeMb.collectAsStateWithLifecycle()
    val profileNickname by viewModel.profileNickname.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    SettingsScreen(
        operationError = operationError,
        isAuthenticated = authenticated,
        onLogin = onLogin,
        onLogout = onLogout,
        themeMode = themeMode,
        onThemeModeChanged = viewModel::setThemeMode,
        themePalette = themePalette,
        onThemePaletteChanged = viewModel::setThemePalette,
        videoCacheSizeMb = videoCacheSizeMb,
        profileNickname = profileNickname,
        onSaveProfileNickname = viewModel::setProfileNickname,
        onVideoCacheSizeChanged = viewModel::setVideoCacheSizeMb,
        onResetSettings = { onSuccess ->
            viewModel.resetPreferences {
                onSuccess()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        },
        onBack = onBack,
    )
}

@Composable
internal fun SettingsScreen(
    operationError: Boolean,
    isAuthenticated: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    themePalette: ThemePalette,
    onThemePaletteChanged: (ThemePalette) -> Unit,
    videoCacheSizeMb: Int,
    profileNickname: String,
    onSaveProfileNickname: (String) -> Unit,
    onVideoCacheSizeChanged: (Int, () -> Unit) -> Unit,
    onResetSettings: (() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedInfo by rememberSaveable { mutableStateOf<SettingsInfo?>(null) }
    var showPersonalCenter by rememberSaveable { mutableStateOf(false) }
    var feedbackDraft by rememberSaveable { mutableStateOf("") }
    var customCacheSizeInput by rememberSaveable(videoCacheSizeMb) {
        mutableStateOf(
            videoCacheSizeMb
                .takeIf { it !in SettingsRepository.VIDEO_CACHE_PRESET_OPTIONS_MB }
                ?.toString()
                .orEmpty(),
        )
    }
    var customCacheSizeError by remember(videoCacheSizeMb) { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = showPersonalCenter) { showPersonalCenter = false }

    Scaffold(snackbarHost = { AppSnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (operationError) Text(resources.getString(R.string.settings_save_failed),
                color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                if (showPersonalCenter) showPersonalCenter = false else onBack()
            }) {
                Text(resources.getString(
                    if (showPersonalCenter) R.string.account_back_to_settings else R.string.ui_settingsscreen_1,
                ))
            }
            Text(
                resources.getString(
                    if (showPersonalCenter) R.string.personal_center_title else R.string.ui_settingsscreen_2,
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (showPersonalCenter) {
                PersonalCenterContent(
                    nickname = profileNickname,
                    isAuthenticated = isAuthenticated,
                    onSaveNickname = onSaveProfileNickname,
                    onLogin = onLogin,
                    onLogout = onLogout,
                )
            } else {
                Text(resources.getString(R.string.account_section), style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProfileAvatar(
                                nickname = profileNickname,
                                fallback = resources.getString(R.string.profile_default_nickname),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    profileNickname.ifBlank { resources.getString(R.string.profile_default_nickname) },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    resources.getString(
                                        if (isAuthenticated) R.string.account_signed_in else R.string.account_guest,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        TextButton(onClick = { showPersonalCenter = true }) {
                            Text(resources.getString(R.string.account_open_profile))
                        }
                    }
                }
                Text(resources.getString(R.string.info_section), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { selectedInfo = SettingsInfo.HELP }) {
                    Text(resources.getString(R.string.info_help_feedback))
                }
                TextButton(onClick = { selectedInfo = SettingsInfo.PRIVACY }) {
                    Text(resources.getString(R.string.info_privacy))
                }
                TextButton(onClick = { selectedInfo = SettingsInfo.TERMS }) {
                    Text(resources.getString(R.string.info_terms))
                }
                TextButton(onClick = { selectedInfo = SettingsInfo.ABOUT }) {
                    Text(resources.getString(R.string.info_about))
                }
                Text(resources.getString(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.System -> R.string.appearance_system
                            ThemeMode.Light -> R.string.appearance_light
                            ThemeMode.Dark -> R.string.appearance_dark
                        }
                        FilterChip(selected = themeMode == mode, onClick = { onThemeModeChanged(mode) },
                            label = { Text(resources.getString(label)) })
                    }
                }
                Text(resources.getString(R.string.appearance_palette), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePalette.entries.forEach { palette ->
                        val label = when (palette) {
                            ThemePalette.Blue -> R.string.appearance_blue
                            ThemePalette.Green -> R.string.appearance_green
                            ThemePalette.Purple -> R.string.appearance_purple
                        }
                        FilterChip(selected = themePalette == palette,
                            onClick = { onThemePaletteChanged(palette) },
                            label = { Text(resources.getString(label)) })
                    }
                }
                Text(resources.getString(R.string.appearance_language), style = MaterialTheme.typography.titleMedium)
                val selectedLanguage = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to R.string.appearance_system_language,
                        "zh" to R.string.appearance_chinese,
                        "en" to R.string.appearance_english).forEach { (tag, label) ->
                        FilterChip(selected = selectedLanguage == tag,
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(tag),
                                )
                            },
                            label = { Text(resources.getString(label)) })
                    }
                }
                Text(resources.getString(R.string.ui_settingsscreen_5), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsRepository.VIDEO_CACHE_PRESET_OPTIONS_MB.forEach { sizeMb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = videoCacheSizeMb == sizeMb,
                                    role = Role.RadioButton,
                                    onClick = {
                                        customCacheSizeError = null
                                        onVideoCacheSizeChanged(sizeMb) {}
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = videoCacheSizeMb == sizeMb,
                                onClick = null,
                            )
                            Text(if (sizeMb == 0) resources.getString(R.string.ui_settingsscreen_6) else "$sizeMb MB")
                        }
                    }
                }
                Text(resources.getString(R.string.ui_settingsscreen_7), style = MaterialTheme.typography.titleMedium)
                if (videoCacheSizeMb !in SettingsRepository.VIDEO_CACHE_PRESET_OPTIONS_MB) {
                    Text(
                        resources.getString(R.string.current_cache_limit, videoCacheSizeMb),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customCacheSizeInput,
                        onValueChange = { value ->
                            customCacheSizeInput = value.filter(Char::isDigit).take(5)
                            customCacheSizeError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(resources.getString(R.string.ui_settingsscreen_8)) },
                        supportingText = { Text("${SettingsRepository.MIN_CUSTOM_VIDEO_CACHE_SIZE_MB}–${SettingsRepository.MAX_CUSTOM_VIDEO_CACHE_SIZE_MB} MB") },
                        singleLine = true,
                        isError = customCacheSizeError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Button(
                        enabled = customCacheSizeInput.isNotBlank() &&
                            customCacheSizeInput.toIntOrNull() != videoCacheSizeMb,
                        onClick = {
                            val sizeMb = customCacheSizeInput.toIntOrNull()
                            if (sizeMb == null || sizeMb !in
                                SettingsRepository.MIN_CUSTOM_VIDEO_CACHE_SIZE_MB..
                                SettingsRepository.MAX_CUSTOM_VIDEO_CACHE_SIZE_MB
                            ) {
                                customCacheSizeError = resources.getString(R.string.ui_settingsscreen_9)
                            } else {
                                customCacheSizeError = null
                                onVideoCacheSizeChanged(sizeMb) {
                                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.cache_limit_saved, sizeMb)) }
                                }
                            }
                        },
                    ) {
                        Text(resources.getString(R.string.ui_settingsscreen_10))
                    }
                }
                customCacheSizeError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    resources.getString(R.string.ui_settingsscreen_11),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { showResetDialog = true }) { Text(resources.getString(R.string.ui_settingsscreen_12)) }
            }
        }
    }

    if (showResetDialog) {
        AppConfirmDialog(
            title = resources.getString(R.string.ui_settingsscreen_13),
            message = resources.getString(R.string.reset_settings_message, SettingsRepository.DEFAULT_VIDEO_CACHE_SIZE_MB),
            confirmLabel = resources.getString(R.string.ui_settingsscreen_14),
            onConfirm = {
                onResetSettings {
                    showResetDialog = false
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.ui_settingsscreen_15)) }
                }
            },
            onDismiss = { showResetDialog = false },
        )
    }

    selectedInfo?.let { info ->
        val (title, message) = when (info) {
            SettingsInfo.HELP -> R.string.info_help_feedback to R.string.info_help_content
            SettingsInfo.PRIVACY -> R.string.info_privacy to R.string.info_privacy_content
            SettingsInfo.TERMS -> R.string.info_terms to R.string.info_terms_content
            SettingsInfo.ABOUT -> R.string.info_about to R.string.info_about_content
        }
        val appVersion = runCatching { context.installedVersionName() }
            .getOrNull().orEmpty().ifBlank { resources.getString(R.string.about_version_unknown) }
        val description = if (info == SettingsInfo.ABOUT) {
            val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
            resources.getString(message, appName, appVersion)
        } else resources.getString(message)
        if (info != SettingsInfo.HELP) {
            AppInfoDialog(resources.getString(title), description,
                onDismiss = { selectedInfo = null },
                closeLabel = resources.getString(R.string.close_info))
            return@let
        }
        AlertDialog(
            onDismissRequest = { selectedInfo = null },
            title = { Text(resources.getString(title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(description)
                    if (info == SettingsInfo.HELP) {
                        OutlinedTextField(
                            value = feedbackDraft,
                            onValueChange = { feedbackDraft = it.take(2_000) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            label = { Text(resources.getString(R.string.feedback_input_label)) },
                            minLines = 3,
                            maxLines = 6,
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow {
                    if (info == SettingsInfo.HELP) {
                        TextButton(
                            enabled = feedbackDraft.isNotBlank(),
                            onClick = {
                                val feedbackIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        resources.getString(R.string.feedback_subject),
                                    )
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        feedbackDraft.trim().ifBlank {
                                            resources.getString(R.string.feedback_body)
                                        },
                                    )
                                }
                                try {
                                    context.startActivity(
                                        Intent.createChooser(
                                            feedbackIntent,
                                            resources.getString(R.string.info_help_feedback),
                                        ),
                                    )
                                    selectedInfo = null
                                } catch (_: ActivityNotFoundException) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(R.string.feedback_share_unavailable))
                                    }
                                }
                            },
                        ) {
                            Text(resources.getString(R.string.send_feedback))
                        }
                    }
                    TextButton(onClick = { selectedInfo = null }) {
                        Text(resources.getString(R.string.close_info))
                    }
                }
            },
        )
    }
}

@Composable
private fun PersonalCenterContent(
    nickname: String,
    isAuthenticated: Boolean,
    onSaveNickname: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    var nicknameDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val editedNickname = nicknameDraft ?: nickname
    var nicknameTouched by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    val normalizedNickname = editedNickname.trim()
    val nicknameValid = SettingsRepository.isValidProfileNickname(normalizedNickname)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(
                    nickname = nickname,
                    fallback = resources.getString(R.string.profile_default_nickname),
                )
                Column {
                    Text(
                        nickname.ifBlank { resources.getString(R.string.profile_default_nickname) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        resources.getString(
                            if (isAuthenticated) R.string.account_signed_in else R.string.account_guest,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedTextField(
                value = editedNickname,
                onValueChange = {
                    nicknameDraft = it.takeCodePoints(SettingsRepository.MAX_PROFILE_NICKNAME_LENGTH + 1)
                    nicknameTouched = true
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(resources.getString(R.string.profile_nickname_label)) },
                supportingText = { Text(resources.getString(R.string.profile_nickname_local_note)) },
                singleLine = true,
                isError = nicknameTouched && !nicknameValid,
            )
            if (nicknameTouched && !nicknameValid) {
                Text(
                    resources.getString(
                        if (normalizedNickname.isEmpty()) R.string.profile_nickname_required
                        else if (normalizedNickname.codePointCount(0, normalizedNickname.length) >
                            SettingsRepository.MAX_PROFILE_NICKNAME_LENGTH) R.string.profile_nickname_too_long
                        else R.string.profile_nickname_invalid,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { onSaveNickname(normalizedNickname) },
                enabled = nicknameValid && normalizedNickname != nickname,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(resources.getString(R.string.profile_save_nickname))
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(resources.getString(R.string.account_section), style = MaterialTheme.typography.titleMedium)
            Text(
                resources.getString(R.string.account_session_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isAuthenticated) {
                TextButton(onClick = { showLogoutConfirmation = true }) {
                    Text(resources.getString(R.string.account_sign_out))
                }
            } else {
                Button(onClick = onLogin) {
                    Text(resources.getString(R.string.account_sign_in))
                }
            }
        }
    }

    if (showLogoutConfirmation) {
        AppConfirmDialog(
            title = resources.getString(R.string.account_sign_out),
            message = resources.getString(R.string.account_sign_out_confirmation),
            confirmLabel = resources.getString(R.string.account_sign_out),
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false },
        )
    }
}

@Composable
private fun ProfileAvatar(nickname: String, fallback: String) {
    val initial = nickname.ifBlank { fallback }.takeCodePoints(1).uppercase()
    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun String.takeCodePoints(maximum: Int): String {
    val codePointCount = codePointCount(0, length)
    if (codePointCount <= maximum) return this
    return substring(0, offsetByCodePoints(0, maximum))
}

@Suppress("DEPRECATION")
private fun android.content.Context.installedVersionName(): String? =
    packageManager.getPackageInfo(packageName, 0).versionName

private enum class SettingsInfo {
    HELP,
    PRIVACY,
    TERMS,
    ABOUT,
}
