package com.example.onread.ui.screens

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.GroupEntity
import com.example.onread.ui.viewmodel.ContactsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = viewModel()
) {
    val contacts by viewModel.contactSettings.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val defaultDelayMinutes by viewModel.defaultDelayMinutes.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactSettingEntity?>(null) }

    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    val filteredContacts = contacts.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery, ignoreCase = true)
    }.sortedBy { it.displayName.lowercase() }

    val isSyncing by viewModel.isSyncing.collectAsState()

    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = -360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1000,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotation.snapTo(0f)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Page title and Sync button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contacts & Rules",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        if (contactsPermissionState.status.isGranted) {
                            viewModel.syncContacts()
                        } else {
                            contactsPermissionState.launchPermissionRequest()
                        }
                    },
                    enabled = !isSyncing
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "Sync Contacts",
                        modifier = Modifier.graphicsLayer {
                            rotationZ = rotation.value
                        }
                    )
                }
            }

            // Permission banner if not granted
            if (!contactsPermissionState.status.isGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Contacts Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant permission to import and configure reminders for your device contacts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { contactsPermissionState.launchPermissionRequest() }) {
                            Text("Grant Permission")
