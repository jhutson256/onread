package com.example.onread.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.GroupEntity
import com.example.onread.ui.viewmodel.GroupsViewModel
import java.util.Locale
import kotlin.math.roundToInt
import java.util.UUID

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val defaultDelayMinutes by viewModel.defaultDelayMinutes.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedGroupForEdit by remember { mutableStateOf<GroupEntity?>(null) }
    var selectedGroupForMembers by remember { mutableStateOf<GroupEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Group")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contact Groups",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No contact groups created",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Create Group")
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
                    items(groups, key = { it.id }) { group ->
                        val memberCount = contacts.count { it.groupId == group.id }
                        GroupItemCard(
                            group = group,
                            memberCount = memberCount,
                            defaultDelayMinutes = defaultDelayMinutes,
                            onClick = { selectedGroupForEdit = group },
                            onManageMembers = { selectedGroupForMembers = group },
                            onDelete = { viewModel.deleteGroup(group) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        GroupEditDialog(
            group = null,
            defaultDelayMinutes = defaultDelayMinutes,
            onDismiss = { showCreateDialog = false },
            onSave = { newGroup ->
                viewModel.insertOrUpdateGroup(newGroup)
                showCreateDialog = false
            }
        )
    }

    selectedGroupForEdit?.let { group ->
        GroupEditDialog(
            group = group,
            defaultDelayMinutes = defaultDelayMinutes,
            onDismiss = { selectedGroupForEdit = null },
            onSave = { updated ->
                viewModel.insertOrUpdateGroup(updated)
                selectedGroupForEdit = null
            }
        )
    }

    selectedGroupForMembers?.let { group ->
        GroupMembersDialog(
            group = group,
            contacts = contacts,
            onDismiss = { selectedGroupForMembers = null },
            onToggleContact = { contactId: String, inGroup: Boolean ->
                viewModel.assignContactToGroup(contactId, if (inGroup) group.id else null)
            }
        )
    }
}

@Composable
fun GroupItemCard(
    group: GroupEntity,
    memberCount: Int,
    defaultDelayMinutes: Int,
    onClick: () -> Unit,
    onManageMembers: () -> Unit,
    onDelete: () -> Unit
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
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$memberCount contacts assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val scheduleText = if (group.useSpecificTime) {
                    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", group.specificHour, group.specificMinute)
                    "Schedule: Specific Time ($formattedTime)"
                } else {
                    val delay = group.customDelayMinutes ?: defaultDelayMinutes
                    val delayFormatted = when {
                        delay < 60 -> "$delay minutes"
                        delay % 60 == 0 -> "${delay / 60} hours"
                        else -> "${delay / 60} hr ${delay % 60} min"
                    }
                    val delayType = if (group.customDelayMinutes != null) "Custom" else "Default"
                    "Delay: $delayFormatted ($delayType)"
                }
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onManageMembers, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Members")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Group", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun GroupEditDialog(
    group: GroupEntity?,
    defaultDelayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (GroupEntity) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var isEnabled by remember { mutableStateOf(group?.isReminderEnabled ?: true) }
    var useSpecificTime by remember { mutableStateOf(group?.useSpecificTime ?: false) }
    var specificHour by remember { mutableIntStateOf(group?.specificHour ?: 9) }
    var specificMinute by remember { mutableIntStateOf(group?.specificMinute ?: 0) }
    var useCustomDelay by remember { mutableStateOf(group?.customDelayMinutes != null) }
    var delayMinutes by remember { mutableFloatStateOf((group?.customDelayMinutes ?: defaultDelayMinutes).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Create Group" else "Edit Group") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    Text("Use Specific Clock Time")
                    Switch(checked = useSpecificTime, onCheckedChange = { useSpecificTime = it })
                }

                if (useSpecificTime) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trigger Time: ${String.format(Locale.getDefault(), "%02d:%02d", specificHour, specificMinute)}", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { specificHour = (specificHour + 1) % 24 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hour: ${String.format("%02d", specificHour)}")
                        }
                        OutlinedButton(
                            onClick = { specificMinute = (specificMinute + 15) % 60 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Min: ${String.format("%02d", specificMinute)}")
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
                            text = "Delay: ${
                                when {
                                    delayMinutes.toInt() < 60 -> "${delayMinutes.toInt()} minutes"
                                    delayMinutes.toInt() % 60 == 0 -> "${delayMinutes.toInt() / 60} hours"
                                    else -> "${delayMinutes.toInt() / 60} hr ${delayMinutes.toInt() % 60} min"
                                }
                            }",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Slider(
                            value = currentIndex.toFloat(),
                            onValueChange = { value ->
                                delayMinutes = delayOptions[value.roundToInt()].toFloat()
                            },
                            valueRange = 0f..delayOptions.lastIndex.toFloat(),
                            steps = 0
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val groupId = group?.id ?: UUID.randomUUID().toString()
                        onSave(
                            GroupEntity(
                                id = groupId,
                                name = name.trim(),
                                isReminderEnabled = isEnabled,
                                customDelayMinutes = if (!useSpecificTime && useCustomDelay) delayMinutes.toInt() else null,
                                useSpecificTime = useSpecificTime,
                                specificHour = specificHour,
                                specificMinute = specificMinute
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
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

@Composable
fun GroupMembersDialog(
    group: GroupEntity,
    contacts: List<ContactSettingEntity>,
    onDismiss: () -> Unit,
    onToggleContact: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Members of ${group.name}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(contacts, key = { it.contactId }) { contact ->
                    val isInGroup = contact.groupId == group.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleContact(contact.contactId, !isInGroup) }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = contact.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(
                            checked = isInGroup,
                            onCheckedChange = { checked -> onToggleContact(contact.contactId, checked) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
