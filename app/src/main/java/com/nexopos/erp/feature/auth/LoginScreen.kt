package com.nexopos.erp.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nexopos.erp.ui.theme.appColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.repo.AuthState
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppTextField

/**
 * Login screen composable for user authentication.
 * 
 * Features:
 * - Email and password fields
 * - Server URL configuration (expandable advanced section)
 * - Loading state during authentication
 * - Error handling with snackbar
 * - Keyboard navigation support
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel
) {
    val loginState by viewModel.loginState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val savedServerUrl by viewModel.serverUrl.collectAsState()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var serverUrlError by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Initialize server URL from saved value
    LaunchedEffect(savedServerUrl) {
        if (savedServerUrl.isNotBlank()) {
            serverUrl = savedServerUrl
        }
    }

    // Handle login success
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }

    // Handle login errors
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Error) {
            snackbarHostState.showSnackbar((loginState as LoginState.Error).message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App title/logo area
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.appColors.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Email field
            AppTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email),
                placeholder = stringResource(R.string.email_placeholder),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                enabled = loginState !is LoginState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password),
                placeholder = stringResource(R.string.password_placeholder),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        performLogin(
                            email = email,
                            password = password,
                            serverUrl = serverUrl.takeIf { it.isNotBlank() },
                            viewModel = viewModel,
                            onUrlError = { error -> serverUrlError = error }
                        )
                    }
                ),
                enabled = loginState !is LoginState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced settings section (expandable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.advanced_settings),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                IconButton(
                    onClick = { showAdvanced = !showAdvanced }
                ) {
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showAdvanced) "Hide advanced settings" else "Show advanced settings",
                        tint = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = showAdvanced,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    AppTextField(
                        value = serverUrl,
                        onValueChange = { 
                            serverUrl = it
                            serverUrlError = null
                        },
                        label = stringResource(R.string.server_url),
                        placeholder = stringResource(R.string.server_url_placeholder),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                performLogin(
                                    email = email,
                                    password = password,
                                    serverUrl = serverUrl.takeIf { it.isNotBlank() },
                                    viewModel = viewModel,
                                    onUrlError = { error -> serverUrlError = error }
                                )
                            }
                        ),
                        isError = serverUrlError != null,
                        supportingText = serverUrlError?.let { { Text(it) } },
                        enabled = loginState !is LoginState.Loading
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login button
            AppButtonPrimary(
                onClick = {
                    performLogin(
                        email = email,
                        password = password,
                        serverUrl = serverUrl.takeIf { it.isNotBlank() },
                        viewModel = viewModel,
                        onUrlError = { error -> serverUrlError = error }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState !is LoginState.Loading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.appColors.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.login_button))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Forgot password link
            AppButtonTertiary(
                onClick = { /* Navigate to forgot password */ },
                enabled = loginState !is LoginState.Loading
            ) {
                Text(stringResource(R.string.forgot_password))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error message display
            if (loginState is LoginState.Error) {
                Text(
                    text = (loginState as LoginState.Error).message,
                    color = MaterialTheme.appColors.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Helper function to perform login with URL validation.
 */
private fun performLogin(
    email: String,
    password: String,
    serverUrl: String?,
    viewModel: AuthViewModel,
    onUrlError: (String) -> Unit
) {
    // Validate URL if provided
    if (!serverUrl.isNullOrBlank()) {
        if (!viewModel.isValidUrl(serverUrl)) {
            onUrlError("Please enter a valid URL (e.g., https://example.com/)")
            return
        }
    }
    
    viewModel.login(email, password, serverUrl)
}
