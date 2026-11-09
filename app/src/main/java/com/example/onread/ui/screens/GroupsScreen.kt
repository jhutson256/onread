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
import kotlin.math.roundToInt
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
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
            onToggleContact = { contactId, inGroup ->
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
                    val formattedTime = String.format("%02d:%02d", group.specificHour, group.specificMinute)
                    "Schedule: Specific Time ($formattedTime)"
                } else {
                    val delay = group.customDelayMinutes ?: defaultDelayMinutes
                    val delayFormatted = when {
                        delay < 60 -> "$delay minutes"
                        delay % 60 == 0 -> "${delay / 60} hours"
                        else -> "${delay / 60} hr${delay % 60} min"
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
    var specificHour by remember { mutableStateOf(group?.specificHour ?: 9) }
    var specificMinute by remember { mutableStateOf(group?.specificMinute ?: 0) }
    var useCustomDelay by remember { mutableStateOf(group?.customDelayMinutes != null) }
    var delayMinutes by remember { mutableStateOf((group?.customDelayMinutes ?: defaultDelayMinutes).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title
