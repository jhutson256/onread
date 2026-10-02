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

    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactSettingEntity?>(null) }

    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    val filteredContacts = contacts.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery, ignoreCase = true)
    }

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
                val delayText = if (contact.customDelayMinutes != null) {
                    "Delay: ${contact.customDelayMinutes}m (Custom)"
                } else {
                    "Delay: ${defaultDelayMinutes}m (Default)"
                }
                Text(
                    text = delayText,
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
    onDismiss: () -> Unit,
    onSave: (ContactSettingEntity) -> Unit
) {
    var isEnabled by remember { mutableStateOf(contact.isReminderEnabled) }
    var useCustomDelay by remember { mutableStateOf(contact.customDelayMinutes != null) }
    var delayMinutes by remember { mutableStateOf((contact.customDelayMinutes ?: defaultDelayMinutes).toFloat()) }

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
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    contact.copy(
                        isReminderEnabled = isEnabled,
                        customDelayMinutes = if (useCustomDelay) delayMinutes.toInt() else null
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
