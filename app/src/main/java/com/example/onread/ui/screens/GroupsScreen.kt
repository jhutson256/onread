package com.example.onread.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onread.data.entity.ContactGroupEntity
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.ui.viewmodel.GroupsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val contacts by viewModel.contactSettings.collectAsState()
    val defaultDelayMinutes by viewModel.defaultDelayMinutes.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ContactGroupEntity?>(null) }
    var managingGroupContacts by remember { mutableStateOf<ContactGroupEntity?>(null) }

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
                    .padding(top = 8.dp, bottom = 8.dp),
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
                    items(groups, key = { it.groupId }) { group ->
                        val groupContactCount = contacts.count { it.groupId == group.groupId }
                        GroupItemCard(
                            group = group,
                            contactCount = groupContactCount,
                            onClick = { managingGroupContacts = group },
                            onEdit = { editingGroup = group },
                            onDelete = { viewModel.deleteGroup(group) },
                            onToggleReminder = { enabled ->
                                viewModel.updateGroup(group.copy(isReminderEnabled = enabled))
                            }
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
            onSave = { name, isEnabled, delay ->
                viewModel.createGroup(name, isEnabled, delay)
                showCreateDialog = false
            }
        )
    }

    editingGroup?.let { group ->
        GroupEditDialog(
            group = group,
            defaultDelayMinutes = defaultDelayMinutes,
            onDismiss = { editingGroup = null },
            onSave = { name, isEnabled, delay ->
                viewModel.updateGroup(
                    group.copy(
                        name = name,
                        isReminderEnabled = isEnabled,
                        delayMinutes = delay
                    )
                )
                editingGroup = null
            }
        )
    }

    managingGroupContacts?.let { group ->
        GroupContactsManagementDialog(
            group = group,
            contacts = contacts,
            onDismiss = { managingGroupContacts = null },
            onToggleContactAssignment = { contact, assigned ->
                viewModel.assignContactToGroup(contact.contactId, if (assigned) group.groupId else null)
            }
        )
    }
}

@Composable
fun GroupItemCard(
    group: ContactGroupEntity,
    contactCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$contactCount contacts • Delay: ${group.delayMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = group.isReminderEnabled,
                    onCheckedChange = onToggleReminder
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Group", tint = MaterialTheme.colorScheme.primary)
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
    group: ContactGroupEntity?,
    defaultDelayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, Int) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var isEnabled by remember { mutableStateOf(group?.isReminderEnabled ?: true) }
    var delayMinutes by remember { mutableStateOf((group?.delayMinutes ?: defaultDelayMinutes).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Create Contact Group" else "Edit Group") },
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
                Text("Reminder Delay: ${delayMinutes.toInt()} minutes", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = delayMinutes,
                    onValueChange = { delayMinutes = it },
                    valueRange = 1f..120f,
                    steps = 119
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), isEnabled, delayMinutes.toInt())
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
fun GroupContactsManagementDialog(
    group: ContactGroupEntity,
    contacts: List<ContactSettingEntity>,
    onDismiss: () -> Unit,
    onToggleContactAssignment: (ContactSettingEntity, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredContacts = contacts.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Contacts in ${group.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (filteredContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No contacts found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredContacts, key = { it.contactId }) { contact ->
                            val isAssigned = contact.groupId == group.groupId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleContactAssignment(contact, !isAssigned) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(
                                    checked = isAssigned,
                                    onCheckedChange = { checked -> onToggleContactAssignment(contact, checked) }
                                )
                            }
                        }
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
