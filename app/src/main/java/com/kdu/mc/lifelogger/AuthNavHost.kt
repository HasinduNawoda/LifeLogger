package com.kdu.mc.lifelogger

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdu.mc.lifelogger.auth.AuthRepository
import com.kdu.mc.lifelogger.auth.LoginScreen
import com.kdu.mc.lifelogger.auth.RegisterScreen

// ─────────────────────────────────────────────────────────────────────────────
// Auth navigation state
// ─────────────────────────────────────────────────────────────────────────────
enum class AuthScreen { SPLASH, LOGIN, REGISTER }

/**
 * Auth navigation host that manages transitions between Splash, Login, and Register screens.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthNavHost(
    authRepository: AuthRepository,
    onAuthComplete: () -> Unit
) {
    var current by remember { mutableStateOf(AuthScreen.SPLASH) }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            slideInHorizontally(
                initialOffsetX = { if (forward) it else -it },
                animationSpec = tween(320)
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { if (forward) -it else it },
                animationSpec = tween(320)
            )
        },
        label = "auth_transition"
    ) { screen ->
        when (screen) {
            AuthScreen.SPLASH -> AuthSplashScreen(
                onLoginClick    = { current = AuthScreen.LOGIN },
                onRegisterClick = { current = AuthScreen.REGISTER }
            )
            AuthScreen.LOGIN -> LoginScreen(
                authRepository       = authRepository,
                onLoggedIn           = { onAuthComplete() },
                onNavigateToRegister = { current = AuthScreen.REGISTER },
                onBack               = { current = AuthScreen.SPLASH }
            )
            AuthScreen.REGISTER -> RegisterScreen(
                authRepository    = authRepository,
                onRegistered      = { onAuthComplete() },
                onNavigateToLogin = { current = AuthScreen.LOGIN },
                onBack            = { current = AuthScreen.SPLASH }
            )
        }
    }
}

@Composable
fun AuthSplashScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Life Logger",
            style = TextStyle(
                brush = AppGradient,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Capture every moment of your journey",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppGradient, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Log In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRegisterClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
        ) {
            Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}
