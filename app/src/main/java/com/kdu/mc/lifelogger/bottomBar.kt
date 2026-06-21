package com.kdu.mc.lifelogger

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size

@Composable
fun AppBottomBar(
    currentScreen: String,
    onHomeClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onProfileClick: () -> Unit,
    language: AppLanguage
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = currentScreen == "calendar",
            onClick = onCalendarClick,
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.calendar),
                    contentDescription = "Calendar",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text(textOf(language, "calendar")) }
        )
        NavigationBarItem(
            selected = currentScreen == "home",
            onClick = onHomeClick,
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.homeicon),
                    contentDescription = "Home",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text(textOf(language, "home")) }
        )
        NavigationBarItem(
            selected = currentScreen == "profile",
            onClick = onProfileClick,
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.user),
                    contentDescription = "Profile",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text(textOf(language, "profile")) }
        )
    }
}