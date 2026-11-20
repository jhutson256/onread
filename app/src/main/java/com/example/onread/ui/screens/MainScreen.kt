package com.example.onread.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.onread.R
import com.example.onread.ui.viewmodel.MainViewModel
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object ContactsRoute

@Serializable
data object GroupsRoute

@Serializable
data object SettingsRoute

@Composable
fun MainScreen(
    initialIntent: Intent? = null,
    viewModel: MainViewModel = viewModel()
) {
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()

    // True while someone replays the tutorial from Settings.
    var isReplayingTutorial by rememberSaveable { mutableStateOf(false) }

    val backStack = remember { mutableStateListOf<Any>(DashboardRoute) }

    // Onboarding tutorial: shown on first launch, or when replayed from Settings.
    if (hasSeenOnboarding == false || isReplayingTutorial) {
        OnboardingScreen(
            isFirstRun = !isReplayingTutorial,
            onFinish = { enableAllContacts ->
                if (isReplayingTutorial) {
                    // Replay: just close it. Don't touch contact settings.
                    isReplayingTutorial = false
                } else {
                    viewModel.setOnboardingCompleted(enableAllContacts)
                }
            }
        )
        return
    }

    LaunchedEffect(initialIntent) {
        if (initialIntent?.getBooleanExtra("OPEN_DASHBOARD", false) == true) {
            if (backStack.lastOrNull() !is DashboardRoute) {
                backStack.clear()
                backStack.add(DashboardRoute)
            }
        }
    }

    val currentKey = backStack.lastOrNull() ?: DashboardRoute

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.onread_logo_transparent),
                    contentDescription = "ONread Logo",
                    modifier = Modifier.height(60.dp),
                    contentScale = ContentScale.Fit
                )
            }
        },

        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentKey is DashboardRoute,
                    onClick = {
                        if (currentKey !is DashboardRoute) {
                            backStack.clear()
                            backStack.add(DashboardRoute)
                        }
                    },
                    icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = "Reminders") },
                    label = { Text("Reminders") }
                )
                NavigationBarItem(
                    selected = currentKey is ContactsRoute,
                    onClick = {
                        if (currentKey !is ContactsRoute) {
                            backStack.clear()
                            backStack.add(ContactsRoute)
                        }
                    },
                    icon = { Icon(Icons.Rounded.ContactPhone, contentDescription = "Contacts") },
                    label = { Text("Contacts") }
                )
                NavigationBarItem(
                    selected = currentKey is GroupsRoute,
                    onClick = {
                        if (currentKey !is GroupsRoute) {
                            backStack.clear()
                            backStack.add(GroupsRoute)
                        }
                    },
                    icon = { Icon(Icons.Rounded.Group, contentDescription = "Groups") },
                    label = { Text("Groups") }
                )
                NavigationBarItem(
                    selected = currentKey is SettingsRoute,
                    onClick = {
                        if (currentKey !is SettingsRoute) {
                            backStack.clear()
                            backStack.add(SettingsRoute)
                        }
                    },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            },
            entryProvider = { key ->
                when (key) {
                    is DashboardRoute -> NavEntry(key) {
                        DashboardScreen()
                    }
                    is ContactsRoute -> NavEntry(key) {
                        ContactsScreen()
                    }
                    is GroupsRoute -> NavEntry(key) {
                        GroupsScreen()
                    }
                    is SettingsRoute -> NavEntry(key) {
                        SettingsScreen(
                            onReplayTutorial = { isReplayingTutorial = true }
                        )
                    }
                    else -> NavEntry(Unit) {
                        Text("Unknown Route")
                    }
                }
            }
        )
    }
}