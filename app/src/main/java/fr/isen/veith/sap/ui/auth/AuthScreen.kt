package fr.isen.veith.sap.ui.auth

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.domain.model.User
import fr.isen.veith.sap.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.res.stringResource
import fr.isen.veith.sap.R

/**
 * Écran principal d'authentification.
 *
 * @param onAuthSuccess  Callback appelé quand login/register réussit.
 *                       Passe l'utilisateur connecté pour la navigation.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: (User) -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.successUser) {
        uiState.successUser?.let { onAuthSuccess(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Green900, Color(0xFF1F4A1F), Color(0xFF2A5A2A))
                )
            )
    ) {
        DecorativeBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ── Logo animé ─────────────────────────────────────────
            AnimatedLogo()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text  = "Sap",
                style = MaterialTheme.typography.displayLarge,
                color = Green100
            )
            Text(
                text  = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = Green400,
                letterSpacing = 0.12.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ── Sélecteur d'onglets Login / Register ───────────────
            TabSelector(
                isLoginTab = uiState.isLoginTab,
                onSelectLogin    = { viewModel.selectTab(true) },
                onSelectRegister = { viewModel.selectTab(false) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Formulaire (animé selon l'onglet) ──────────────────
            AnimatedContent(
                targetState = uiState.isLoginTab,
                transitionSpec = {
                    if (targetState) {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    } else {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    }
                },
                label = "form_transition"
            ) { isLogin ->
                if (isLogin) {
                    LoginForm(
                        email          = uiState.loginEmail,
                        password       = uiState.loginPassword,
                        passwordVisible = uiState.loginPwdVisible,
                        isLoading      = uiState.isLoading,
                        onEmailChange    = viewModel::onLoginEmailChange,
                        onPasswordChange = viewModel::onLoginPasswordChange,
                        onTogglePwd      = viewModel::toggleLoginPwdVisibility,
                        onSubmit         = viewModel::login
                    )
                } else {
                    RegisterForm(
                        username       = uiState.regUsername,
                        email          = uiState.regEmail,
                        password       = uiState.regPassword,
                        passwordVisible = uiState.regPwdVisible,
                        isLoading      = uiState.isLoading,
                        onUsernameChange = viewModel::onRegUsernameChange,
                        onEmailChange    = viewModel::onRegEmailChange,
                        onPasswordChange = viewModel::onRegPasswordChange,
                        onTogglePwd      = viewModel::toggleRegPwdVisibility,
                        onSubmit         = viewModel::register
                    )
                }
            }

            // ── Message d'erreur ───────────────────────────────────
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                uiState.errorMessage?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(message = msg, onDismiss = viewModel::clearError)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Logo avec animation d'entrée ─────────────────────────────────────
@Composable
private fun AnimatedLogo() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn()
    ) {
        SapLogo(modifier = Modifier.size(96.dp))
    }
}

// ── Fond décoratif ────────────────────────────────────────────────────
@Composable
private fun DecorativeBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color  = Green800.copy(alpha = 0.35f),
            radius = size.width * 0.55f,
            center = androidx.compose.ui.geometry.Offset(size.width * 1.1f, size.height * 0.08f)
        )
        drawCircle(
            color  = Orange400.copy(alpha = 0.08f),
            radius = size.width * 0.35f,
            center = androidx.compose.ui.geometry.Offset(size.width * (-0.1f), size.height * 0.88f)
        )
    }
}

// ── Sélecteur d'onglets ───────────────────────────────────────────────
@Composable
private fun TabSelector(
    isLoginTab: Boolean,
    onSelectLogin: () -> Unit,
    onSelectRegister: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(4.dp)
    ) {
        TabItem(
            text      = stringResource(R.string.tab_login),
            selected  = isLoginTab,
            modifier  = Modifier.weight(1f),
            onClick   = onSelectLogin
        )
        TabItem(
            text      = stringResource(R.string.tab_register),
            selected  = !isLoginTab,
            modifier  = Modifier.weight(1f),
            onClick   = onSelectRegister
        )
    }
}

@Composable
private fun TabItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Orange400 else Color.Transparent,
        animationSpec = tween(250),
        label = "tab_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else Green200,
        animationSpec = tween(250),
        label = "tab_text"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ── Formulaire de connexion ───────────────────────────────────────────
@Composable
private fun LoginForm(
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePwd: () -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SapTextField(
            value       = email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.hint_email),
            keyboardType = KeyboardType.Email,
            imeAction    = ImeAction.Next,
            onImeAction  = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(12.dp))
        SapTextField(
            value       = password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(R.string.hint_password),
            isPassword   = true,
            passwordVisible = passwordVisible,
            onTogglePwd  = onTogglePwd,
            imeAction    = ImeAction.Done,
            onImeAction  = { focusManager.clearFocus(); onSubmit() }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text     = stringResource(R.string.forgot_password),
            color    = Orange200,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { /* TODO: reset password flow */ }
                .padding(4.dp)
        )
        Spacer(Modifier.height(16.dp))
        SapPrimaryButton(
            text      = stringResource(R.string.btn_login),
            isLoading = isLoading,
            onClick   = onSubmit
        )
    }
}

// ── Formulaire d'inscription ──────────────────────────────────────────
@Composable
private fun RegisterForm(
    username: String,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePwd: () -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SapTextField(
            value       = username,
            onValueChange = onUsernameChange,
            placeholder = stringResource(R.string.hint_username),
            imeAction   = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(12.dp))
        SapTextField(
            value       = email,
            onValueChange = onEmailChange,
            placeholder  = stringResource(R.string.hint_email),
            keyboardType = KeyboardType.Email,
            imeAction    = ImeAction.Next,
            onImeAction  = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(12.dp))
        SapTextField(
            value       = password,
            onValueChange = onPasswordChange,
            placeholder  = stringResource(R.string.hint_password_register),
            isPassword   = true,
            passwordVisible = passwordVisible,
            onTogglePwd  = onTogglePwd,
            imeAction    = ImeAction.Done,
            onImeAction  = { focusManager.clearFocus(); onSubmit() }
        )
        Spacer(Modifier.height(20.dp))
        SapPrimaryButton(
            text      = stringResource(R.string.btn_register),
            isLoading = isLoading,
            onClick   = onSubmit
        )
    }
}

// ── Champ de saisie Sap ──────────────────────────────────────────
@SuppressLint("ComposableNaming")
@Composable
private fun SapTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePwd: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = {
            Text(
                text  = placeholder,
                color = Green200.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction    = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        trailingIcon = if (isPassword && onTogglePwd != null) {
            {
                IconButton(onClick = onTogglePwd) {
                    Icon(
                        imageVector = if (passwordVisible)
                            Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(if (passwordVisible) R.string.pwd_hide else R.string.pwd_show),
                        tint = Green400
                    )
                }
            }
        } else null,
        shape  = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor   = Green50,
            unfocusedTextColor = Green100,
            focusedBorderColor   = Orange400,
            unfocusedBorderColor = Green600.copy(alpha = 0.5f),
            cursorColor          = Orange400,
            focusedContainerColor   = Color.White.copy(alpha = 0.07f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
        ),
        singleLine = true
    )
}

// ── Bouton principal ──────────────────────────────────────────────────
@Composable
private fun SapPrimaryButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick  = onClick,
        enabled  = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = Orange400,
            contentColor           = Color.White,
            disabledContainerColor = Orange400.copy(alpha = 0.6f),
            disabledContentColor   = Color.White.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation  = 0.dp,
            pressedElevation  = 0.dp
        )
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "btn_content"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(22.dp),
                    color     = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text  = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// ── Bannière d'erreur ─────────────────────────────────────────────────
@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF5C1A1A).copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = message,
            color    = Color(0xFFFFB3B3),
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text     = "✕",
            color    = Color(0xFFFFB3B3),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(4.dp)
        )
    }
}

// ── Alias Canvas pour la déco ─────────────────────────────────────────
@Composable
private fun Canvas(modifier: Modifier, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(modifier = modifier, onDraw = block)
}