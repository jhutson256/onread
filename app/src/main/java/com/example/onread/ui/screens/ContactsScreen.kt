package com.example.onread.ui.screens

import android.Manifest
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.ui.viewmodel.ContactsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = viewModel()
) {
    val contacts by viewModel.contactSettings.collectAsState()
    val defaultDelayMinutes by viewModel.defaultDelayMinutes.collectAsState()
    val useSpecificTime by viewModel.useSpecificTime.collectAsState()
    val globalSpecificHour by viewModel.globalSpecificHour.collectAsState()
    val globalSpecificMinute by viewModel.globalSpecificMinute.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactSettingEntity?>(null) }

    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    val filteredContacts = contacts.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery, ignoreCase = true)
    }.sortedBy { it.displayName.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts & Rules", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        if (contactsPermissionState.status.isGranted) {
                            viewModel.syncContacts()
                        } else {
                            contactsPermissionState.launchPermissionRequest()
                        }
                    }) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Sync Contacts")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
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
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.ContactPhone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No contacts imported",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            if (contactsPermissionState.status.isGranted) {
                                viewModel.syncContacts()
                            } else {
                                contactsPermissionState.launchPermissionRequest()
                            }
                        }) {
                            Text("Import Contacts")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredContacts, key = { it.contactId }) { contact ->
                        ContactItemCard(
                            contact = contact,
                            defaultDelayMinutes = defaultDelayMinutes,
                            useSpecificTime = useSpecificTime,
                            globalHour = globalSpecificHour,
                            globalMinute = globalSpecificMinute,
                            onClick = { selectedContact = contact },
                            onToggleReminder = { enabled ->
                                viewModel.updateContactSetting(contact.copy(isReminderEnabled = enabled))
                            }
                        )
                    }
                }
            }
        }
    }

    // Contact Setting Dialog / Bottom Sheet
    selectedContact?.let { contact ->
        ContactSettingDialog(
            contact = contact,
            defaultDelayMinutes = defaultDelayMinutes,
            useSpecificTime = useSpecificTime,
            globalHour = globalSpecificHour,
            globalMinute = globalSpecificMinute,
            onDismiss = { selectedContact = null },
            onSave = { updated ->
                viewModel.updateContactSetting(updated)
                selectedContact = null
            }
        )
    }
}

@Composable
fun ContactItemCard(
    contact: ContactSettingEntity,
    defaultDelayMinutes: Int,
    useSpecificTime: Boolean,
    globalHour: Int,
    globalMinute: Int,
    onClick: () -> Unit,
    onToggleReminder: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val timingText = if (useSpecificTime) {
                    val hour = contact.customSpecificHour ?: globalHour
                    val minute = contact.customSpecificMinute ?: globalMinute
                    val isCustom = contact.customSpecificHour != null
                    val dispHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    val amPm = if (hour >= 12) "PM" else "AM"
                    val timeStr = String.format("%d:%02d %s", dispHour, minute, amPm)
                    if (isCustom) "Time: $timeStr (Custom)" else "Time: $timeStr (Default)"
                } else {
                    val delay = contact.customDelayMinutes ?: defaultDelayMinutes
                    val isCustom = contact.customDelayMinutes != null
                    if (isCustom) "Delay: ${delay}m (Custom)" else "Delay: ${delay}m (Default)"
                }
                Text(
                    text = timingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Switch(
                checked = contact.isReminderEnabled,
                onCheckedChange = onToggleReminder
            )
        }
    }
}

@Composable
fun ContactSettingDialog(
    contact: ContactSettingEntity,
    defaultDelayMinutes: Int,
    useSpecificTime: Boolean,
    globalHour: Int,
    globalMinute: Int,
    onDismiss: () -> Unit,
    onSave: (ContactSettingEntity) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(contact.isReminderEnabled) }

    // For delay mode
    var useCustomDelay by remember { mutableStateOf(contact.customDelayMinutes != null) }
    var delayMinutes by remember { mutableStateOf((contact.customDelayMinutes ?: defaultDelayMinutes).toFloat()) }

    // For specific time mode
    var useCustomTime by remember { mutableStateOf(contact.customSpecificHour != null) }
    val initialHour = contact.customSpecificHour ?: globalHour
    val initialMinute = contact.customSpecificMinute ?: globalMinute
    var customHour by remember { mutableStateOf(initialHour) }
    var customMinute by remember { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${contact.displayName}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Reminders")
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (useSpecificTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom Specific Time")
                        Switch(checked = useCustomTime, onCheckedChange = { useCustomTime = it })
                    }

                    if (useCustomTime) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val dispH = if (customHour == 0) 12 else if (customHour > 12) customHour - 12 else customHour
                        val ap = if (customHour >= 12) "PM" else "AM"
                        val timeStr = String.format("%d:%02d %s", dispH, customMinute, ap)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Target Time", style = MaterialTheme.typography.bodySmall)
                                Text(timeStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Button(onClick = {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        customHour = h
                                        customMinute = m
                                    },
                                    customHour,
                                    customMinute,
                                    false
                                ).show()
                            }) {
                                Text("Pick Time")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom Delay")
                        Switch(checked = useCustomDelay, onCheckedChange = { useCustomDelay = it })
                    }

                    if (useCustomDelay) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Delay: ${delayMinutes.toInt()} minutes", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = delayMinutes,
                            onValueChange = { delayMinutes = it },
                            valueRange = 1f..120f,
                            steps = 119
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    contact.copy(
                        isReminderEnabled = isEnabled,
                        customDelayMinutes = if (!useSpecificTime && useCustomDelay) delayMinutes.toInt() else null,
                        customSpecificHour = if (useSpecificTime && useCustomTime) customHour else null,
                        customSpecificMinute = if (useSpecificTime && useCustomTime) customMinute else null
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
