package com.kdu.mc.lifelogger.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdu.mc.lifelogger.AppGradient
import kotlinx.coroutines.launch

/**
 * Simple email/password auth flow backed by Firebase Auth.
 * Replace styling with your existing design system as needed —
 * functionality (validation, error handling, loading state) is the focus here.
 */
@Composable
fun AuthNavHost(
    authRepository: AuthRepository = AuthRepository(),
    onAuthComplete: () -> Unit
) {
    var showRegister by remember { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            authRepository = authRepository,
            onRegistered = onAuthComplete,
            onNavigateToLogin = { showRegister = false }
        )
    } else {
        LoginScreen(
            authRepository = authRepository,
            onLoggedIn = onAuthComplete,
            onNavigateToRegister = { showRegister = true }
        )
    }
}

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = email.isNotBlank() && password.isNotBlank() && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        Text("Life Logger", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Welcome back", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    brush = if (canSubmit) AppGradient else androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFFBDBDBD), Color(0xFFBDBDBD))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    "Log In",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                        .then(
                            if (canSubmit) Modifier.androidClickable {
                                isLoading = true
                                scope.launch {
                                    try {
                                        authRepository.login(email, password)
                                        onLoggedIn()
                                    } catch (e: Exception) {
                                        errorMessage = authRepository.friendlyError(e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else Modifier
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Don't have an account? Register",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .androidClickable { onNavigateToRegister() }
        )
    }
}

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegistered: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = name.isNotBlank() && email.isNotBlank() &&
        password.length >= 6 && password == confirmPassword && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        Text("Create Account", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Start logging your life", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it; errorMessage = null },
            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; errorMessage = null },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; errorMessage = null },
            label = { Text("Password (min 6 characters)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword, onValueChange = { confirmPassword = it; errorMessage = null },
            label = { Text("Confirm Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            isError = confirmPassword.isNotEmpty() && confirmPassword != password
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    brush = if (canSubmit) AppGradient else androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFFBDBDBD), Color(0xFFBDBDBD))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    "Register",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                        .then(
                            if (canSubmit) Modifier.androidClickable {
                                isLoading = true
                                scope.launch {
                                    try {
                                        authRepository.register(email, password, name)
                                        onRegistered()
                                    } catch (e: Exception) {
                                        errorMessage = authRepository.friendlyError(e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else Modifier
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Already have an account? Log in",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .androidClickable { onNavigateToLogin() }
        )
    }
}

/** Local alias so this file doesn't need an extra import line repeated everywhere. */
private fun Modifier.androidClickable(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
