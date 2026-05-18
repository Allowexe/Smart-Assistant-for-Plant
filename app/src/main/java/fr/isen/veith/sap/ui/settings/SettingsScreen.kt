package fr.isen.veith.sap.ui.settings

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.isen.veith.sap.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.data.preferences.AppLanguage
import fr.isen.veith.sap.data.preferences.AppTheme
import fr.isen.veith.sap.domain.model.Achievement
import fr.isen.veith.sap.ui.theme.*

/**
 * Écran de réglages Sap.
 *
 * @param onLogout  Callback vers l'écran de connexion après déconnexion
 */
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(viewModel) {
        viewModel.languageChanged.collect { activity?.recreate() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header profil ─────────────────────────────────────────
            ProfileHeader(
                username = state.username,
                email    = state.email,
                isSaved  = state.isSaved,
                onUsernameChange = viewModel::onUsernameChange,
                onEmailChange    = viewModel::onEmailChange,
                onSave           = viewModel::saveProfile
            )

            Spacer(Modifier.height(20.dp))

            // ── Préférences ───────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_prefs_section)) {
                SettingsRow(
                    icon     = Icons.Default.Language,
                    label    = stringResource(R.string.settings_language),
                    value    = state.language.label,
                    onClick  = viewModel::showLanguageDialog
                )
                SettingsDivider()
                SettingsRow(
                    icon     = Icons.Default.Palette,
                    label    = stringResource(R.string.settings_theme),
                    value    = state.theme.localizedLabel(),
                    onClick  = viewModel::showThemeDialog
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon     = Icons.Default.Notifications,
                    label    = stringResource(R.string.settings_notifications),
                    checked  = state.notificationsEnabled,
                    onToggle = viewModel::toggleNotifications
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Succès ────────────────────────────────────────────────
            AchievementsSection(achievements = state.achievements)

            Spacer(Modifier.height(20.dp))

            // ── Compte ────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_account_section)) {
                SettingsRow(
                    icon    = Icons.Default.Info,
                    label   = stringResource(R.string.settings_app_version),
                    value   = "1.0.0",
                    onClick = {}
                )
                SettingsDivider()
                SettingsRow(
                    icon      = Icons.AutoMirrored.Filled.Logout,
                    label     = stringResource(R.string.settings_logout),
                    value     = "",
                    textColor = Color(0xFFCF6679),
                    onClick   = viewModel::showLogoutDialog
                )
            }

            Spacer(Modifier.height(40.dp))
        }

        // ── Dialogs ───────────────────────────────────────────────────
        if (state.showThemeDialog) {
            ThemePickerDialog(
                current   = state.theme,
                onSelect  = viewModel::setTheme,
                onDismiss = viewModel::dismissThemeDialog
            )
        }

        if (state.showLanguageDialog) {
            LanguagePickerDialog(
                current   = state.language,
                onSelect  = viewModel::setLanguage,
                onDismiss = viewModel::dismissLanguageDialog
            )
        }

        if (state.showLogoutDialog) {
            LogoutConfirmDialog(
                onConfirm = { viewModel.dismissLogoutDialog(); onLogout() },
                onDismiss = viewModel::dismissLogoutDialog
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Header profil
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ProfileHeader(
    username: String,
    email: String,
    isSaved: Boolean,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var editMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Green900, Color(0xFF253D25))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Avatar avec initiales
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Orange200, Orange400)
                        )
                    )
                    .border(2.dp, Orange400, CircleShape)
                    .clickable { editMode = !editMode },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = username.take(2).uppercase().ifBlank { "?" },
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color      = Orange900
                )
            }

            Spacer(Modifier.height(4.dp))

            // Bouton édition
            TextButton(onClick = { editMode = !editMode }) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = null,
                    tint = Orange200,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = if (editMode) stringResource(R.string.profile_done) else stringResource(R.string.profile_edit),
                    color = Orange200,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Champs éditables (animés)
            AnimatedVisibility(
                visible = editMode,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    ProfileTextField(
                        value         = username,
                        onValueChange = onUsernameChange,
                        placeholder   = stringResource(R.string.hint_username),
                        icon          = Icons.Default.Person
                    )
                    Spacer(Modifier.height(8.dp))
                    ProfileTextField(
                        value         = email,
                        onValueChange = onEmailChange,
                        placeholder   = stringResource(R.string.hint_email),
                        icon          = Icons.Default.Email
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSave(); editMode = false },
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Orange400,
                            contentColor   = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.profile_save))
                    }
                }
            }

            // Affichage statique (non-édition)
            AnimatedVisibility(
                visible = !editMode,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = username.ifBlank { stringResource(R.string.profile_default_name) },
                        style = MaterialTheme.typography.titleLarge,
                        color = Green50
                    )
                    if (email.isNotBlank()) {
                        Text(
                            text  = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Green200.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Feedback "Sauvegardé"
            AnimatedVisibility(
                visible = isSaved,
                enter   = fadeIn() + scaleIn(),
                exit    = fadeOut() + scaleOut()
            ) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Green800.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Green200,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_saved), color = Green200, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = {
            Text(placeholder, color = Green200.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon   = {
            Icon(icon, contentDescription = null, tint = Green400, modifier = Modifier.size(18.dp))
        },
        shape  = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor     = Green50,
            unfocusedTextColor   = Green100,
            focusedBorderColor   = Orange400,
            unfocusedBorderColor = Green600.copy(alpha = 0.5f),
            cursorColor          = Orange400,
            focusedContainerColor   = Color.White.copy(alpha = 0.07f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.07f)
        ),
        singleLine = true
    )
}

// ─────────────────────────────────────────────────────────────────────
// Section générique avec titre et carte
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text     = title.uppercase(),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            shape  = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, Green600.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Ligne de réglage standard (cliquable)
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône dans un cercle coloré
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Green800.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Green400, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = textColor,
            modifier = Modifier.weight(1f)
        )

        if (value.isNotBlank()) {
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Ligne avec toggle switch
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Green800.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Green400, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = Green600,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 66.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}

// ─────────────────────────────────────────────────────────────────────
// Section succès / achievements
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun AchievementsSection(achievements: List<Achievement>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = stringResource(R.string.settings_achievements),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
            Spacer(Modifier.width(8.dp))
            val unlocked = achievements.count { it.isUnlocked }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Orange400.copy(alpha = 0.2f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = "$unlocked / ${achievements.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Orange400
                )
            }
        }

        // Grille 2 colonnes
        val rows = achievements.chunked(2)
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEachIndexed { colIndex, achievement ->
                    AchievementCard(
                        achievement    = achievement,
                        animationDelay = (rowIndex * 2 + colIndex) * 80,
                        modifier       = Modifier.weight(1f)
                    )
                }
                // Remplir si nombre impair
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex < rows.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    animationDelay: Int,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    // Animation de rebond à l'apparition
    val scale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "badge_scale"
    )

    // Pulsation si débloquée
    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = if (achievement.isUnlocked) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (achievement.isUnlocked)
                    Green800.copy(alpha = 0.45f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (achievement.isUnlocked)
                    Orange400.copy(alpha = glowAlpha)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emoji dans un cercle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (achievement.isUnlocked) Orange400.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = achievement.emoji,
                fontSize = 22.sp,
                color    = if (achievement.isUnlocked) Color.Unspecified
                else Color.Unspecified.copy(alpha = 0.4f)
            )
            // Cadenas si verrouillé
            if (!achievement.isUnlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint     = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text      = achievement.title,
            style     = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color      = if (achievement.isUnlocked) Green100
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            maxLines   = 1
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text    = achievement.description,
            style   = MaterialTheme.typography.labelSmall,
            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = if (achievement.isUnlocked) 0.55f else 0.3f),
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun AppTheme.localizedLabel(): String = when (this) {
    AppTheme.LIGHT  -> stringResource(R.string.theme_light)
    AppTheme.DARK   -> stringResource(R.string.theme_dark)
    AppTheme.SYSTEM -> stringResource(R.string.theme_system)
}

@Composable
private fun ThemePickerDialog(
    current: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    SapPickerDialog(title = stringResource(R.string.dialog_theme_title), onDismiss = onDismiss) {
        AppTheme.entries.forEach { theme ->
            PickerOption(
                label      = theme.localizedLabel(),
                icon       = when (theme) {
                    AppTheme.LIGHT  -> "☀️"
                    AppTheme.DARK   -> "🌙"
                    AppTheme.SYSTEM -> "📱"
                },
                isSelected = theme == current,
                onClick    = { onSelect(theme) }
            )
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    SapPickerDialog(title = stringResource(R.string.dialog_language_title), onDismiss = onDismiss) {
        AppLanguage.entries.forEach { lang ->
            PickerOption(
                label      = lang.label,
                icon       = if (lang == AppLanguage.FRENCH) "🇫🇷" else "🇬🇧",
                isSelected = lang == current,
                onClick    = { onSelect(lang) }
            )
        }
    }
}

@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.dialog_logout_title), color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(
                stringResource(R.string.dialog_logout_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_logout_confirm), color = Color(0xFFCF6679))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = Green400)
            }
        }
    )
}

// Dialog générique avec options
@Composable
private fun SapPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        title = {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(content = content)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close), color = Green400)
            }
        }
    )
}

@Composable
private fun PickerOption(
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) Green800.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodyMedium,
            color      = if (isSelected) Green200
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier   = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint     = Orange400,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}