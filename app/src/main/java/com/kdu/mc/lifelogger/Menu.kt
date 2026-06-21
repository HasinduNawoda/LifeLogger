package com.kdu.mc.lifelogger

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DrawerContent(
    language: AppLanguage,
    drawerWidth: Dp,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onNavigate: (String) -> Unit,
    onSyncClick: (String) -> Unit,
    onOffline: (String) -> Unit,
    onLogout: () -> Unit,

    ) {
    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth),
        drawerContainerColor = Color.Transparent,
        drawerShape = RoundedCornerShape(
            topEnd = 28.dp,
            bottomEnd = 28.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 48.dp, horizontal = 24.dp)
            ) {
                Text(
                    text = textOf(language, "app_name"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000)
                )

                Spacer(modifier = Modifier.height(36.dp))

                DrawerMenuItem(painter = painterResource(id = R.drawable.sync), text = textOf(language, "Sync Center")) {
                    scope.launch {
                        drawerState.close()
                        onSyncClick("sync")
                    }
                }
                DrawerMenuItem(
                    painter = painterResource(id = R.drawable.upload),
                    text = textOf(language, "Uploads")
                ) {
                    scope.launch {
                        drawerState.close()
                        onNavigate("uploads")
                    }
                }
                DrawerMenuItem(painter = painterResource(id = R.drawable.offline), text = textOf(language, "Offline")) {
                    scope.launch {
                        drawerState.close()
                        onOffline("offline")
                    }
                }
                DrawerMenuItem(painter = painterResource(id = R.drawable.bin), text = textOf(language, "Trash")) {
                    scope.launch {
                        drawerState.close()
                        onNavigate("trash")
                    }
                }
                DrawerMenuItem(painter = painterResource(id = R.drawable.settings), text = textOf(language, "Settings")) {
                    scope.launch {
                        drawerState.close()
                        //onNavigate("settings")
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                DrawerMenuItem(painter = painterResource(id = R.drawable.logout), text = textOf(language, "Logout")) {
                    scope.launch {
                        drawerState.close()
                        onLogout()
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    painter: Painter? = null,
    emoji: String? = null,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = text,
                modifier = Modifier.size(36.dp)
            )
        } else if (emoji != null) {
            Text(text = emoji, fontSize = 30.sp)
        }

        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF222222)
        )
    }
}