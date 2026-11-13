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

@OptIn(ExperimentalPermissionsApi::class)
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
                        val group = groups.find { it.id == contact.groupId }
                        ContactItemCard(
                            contact = contact,
                            groupName = group?.name,
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

    // Contact Setting Dialog
    selectedContact?.let { contact ->
        ContactSettingDialog(
            contact = contact,
            groups = groups,
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
    groupName: String?,
    defaultDelayMinutes: Int,
    onClick: () -> Unit,
    onToggleReminder: (Boolean) -> Unit
) {
    val effectiveDelayMinutes = contact.customDelayMinutes ?: defaultDelayMinutes
    val delayType = if (contact.customDelayMinutes != null) "Custom" else "Default"

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
                if (!groupName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Group: $groupName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val scheduleText = if (contact.useSpecificTime) {
                    "Specific Time: ${String.format(Locale.getDefault(), "%02d:%02d", contact.specificHour, contact.specificMinute)}"
                } else {
                    val delayFormatted = when {
                        effectiveDelayMinutes < 60 -> "$effectiveDelayMinutes minutes"
                        effectiveDelayMinutes % 60 == 0 -> "${effectiveDelayMinutes / 60} hours"
                        else -> "${effectiveDelayMinutes / 60} hr ${effectiveDelayMinutes % 60} min"
                    }
                    "Delay: $delayFormatted ($delayType)"
                }
                Text(
                    text = scheduleText,
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
    groups: List<GroupEntity>,
    defaultDelayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (ContactSettingEntity) -> Unit
) {
    var isEnabled by remember { mutableStateOf(contact.isReminderEnabled) }
    var selectedGroupId by remember { mutableStateOf(contact.groupId) }
    var useSpecificTime by remember { mutableStateOf(contact.useSpecificTime) }
    var specificHour by remember { mutableIntStateOf(contact.specificHour) }
    var specificMinute by remember { mutableIntStateOf(contact.specificMinute) }
    var useCustomDelay by remember { mutableStateOf(contact.customDelayMinutes != null) }
    var delayMinutes by remember { mutableFloatStateOf((contact.customDelayMinutes ?: defaultDelayMinutes).toFloat()) }
    var expandedGroupDropdown by remember { mutableStateOf(false) }

    val effectiveMinutes = if (useCustomDelay) delayMinutes.toInt() else defaultDelayMinutes
    val delayType = when {
        useSpecificTime -> "Specific Time"
        useCustomDelay -> "Custom"
        else -> "Default"
    }

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

                // Group assignment dropdown
                Text("Group", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedGroupDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val groupName = groups.find { it.id == selectedGroupId }?.name ?: "None (No Group)"
                        Text("Group: $groupName")
                    }
                    DropdownMenu(
                        expanded = expandedGroupDropdown,
                        onDismissRequest = { expandedGroupDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (No Group)") },
                            onClick = {
                                selectedGroupId = null
                                expandedGroupDropdown = false
                            }
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    selectedGroupId = group.id
                                    expandedGroupDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Specific Clock Time")
                    Switch(checked = useSpecificTime, onCheckedChange = { useSpecificTime = it })
                }

                if (useSpecificTime) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Trigger Time: ${String.format(Locale.getDefault(), "%02d:%02d", specificHour, specificMinute)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { specificHour = (specificHour + 1) % 24 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hour: ${String.format(Locale.getDefault(), "%02d", specificHour)}")
                        }
                        OutlinedButton(
                            onClick = { specificMinute = (specificMinute + 15) % 60 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Min: ${String.format(Locale.getDefault(), "%02d", specificMinute)}")
                        }
                    }
                } else {
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
                        val delayOptions = remember {
                            buildList {
                                add(1)
                                add(5)
                                addAll((10..60 step 5).toList())
                                addAll((90..720 step 30).toList())
                                addAll((780..1440 step 60).toList())
                            }
                        }

                        val currentIndex = delayOptions.indexOfFirst {
                            it >= delayMinutes.toInt()
                        }.coerceAtLeast(0)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                delayMinutes < 60 ->
                                    "Delay: ${delayMinutes.toInt()} minutes"

                                delayMinutes.toInt() % 60 == 0 ->
                                    "Delay: ${delayMinutes.toInt() / 60} hours"

                                else ->
                                    "Delay: ${delayMinutes.toInt() / 60} hr ${
                                        delayMinutes.toInt() % 60
                                    } min"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = currentIndex.toFloat(),
                            onValueChange = { value ->
                                delayMinutes =
                                    delayOptions[value.roundToInt()].toFloat()
                            },
                            valueRange = 0f..delayOptions.lastIndex.toFloat(),
                            steps = 0
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Effective Reminder Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Delay Type: $delayType",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!useSpecificTime) {
                            Text(
                                text = "Applied Reminder Time: ${
                                    when {
                                        effectiveMinutes < 60 -> "$effectiveMinutes minutes"
                                        effectiveMinutes % 60 == 0 -> "${effectiveMinutes / 60} hours"
                                        else -> "${effectiveMinutes / 60} hr ${effectiveMinutes % 60} min"
                                    }
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Target Time: ${String.format(Locale.getDefault(), "%02d:%02d", specificHour, specificMinute)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    contact.copy(
                        isReminderEnabled = isEnabled,
                        groupId = selectedGroupId,
                        useSpecificTime = useSpecificTime,
                        specificHour = specificHour,
                        specificMinute = specificMinute,
                        customDelayMinutes = if (!useSpecificTime && useCustomDelay) delayMinutes.toInt() else null
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
