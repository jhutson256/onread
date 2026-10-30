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
import androidx.compose.material.icons.rounded.Person
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

    var showAddDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ContactGroupEntity?>(null) }
    var managingContactsGroup by remember { mutableStateOf<ContactGroupEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
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
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                        Button(onClick = { showAddDialog = true }) {
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
                        val assignedCount = contacts.count { it.groupId == group.id }
                        GroupItemCard(
                            group = group,
                            assignedCount = assignedCount,
                            onEdit = { editingGroup = group },
                            onDelete = { viewModel.deleteGroup(group) },
                            onManageContacts = { managingContactsGroup = group },
                            onToggleReminder = { enabled ->
                                viewModel.updateGroup(group.copy(isReminderEnabled = enabled))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        GroupEditDialog(
            group = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, isReminderEnabled, delay ->
                viewModel.addGroup(name, isReminderEnabled, delay)
                showAddDialog = false
            }
        )
    }

    editingGroup?.let { group ->
        GroupEditDialog(
            group = group,
            onDismiss = { editingGroup = null },
            onSave = { name, isReminderEnabled, delay ->
                viewModel.updateGroup(group.copy(name = name, isReminderEnabled = isReminderEnabled, customDelayMinutes = delay))
                editingGroup = null
            }
        )
    }

    managingContactsGroup?.let { group ->
        GroupContactsDialog(
            group = group,
            contacts = contacts,
            onDismiss = { managingContactsGroup = null },
            onToggleContactAssignment = { contactId, isAssigned ->
                viewModel.assignContactToGroup(contactId, if (isAssigned) group.id else null)
            }
        )
    }
}

@Composable
fun GroupItemCard(
    group: ContactGroupEntity,
    assignedCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageContacts: () -> Unit,
    onToggleReminder: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = "$assignedCount contacts assigned • Delay: ${group.customDelayMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = group.isReminderEnabled,
                    onCheckedChange = onToggleReminder
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onManageContacts) {
                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manage Contacts")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Group")
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
    onDismiss: () -> Unit,
    onSave: (String, Boolean, Int) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var isReminderEnabled by remember { mutableStateOf(group?.isReminderEnabled ?: true) }
    var delayMinutes by remember { mutableStateOf((group?.customDelayMinutes ?: 30).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Create Contact Group" else "Edit Contact Group") },
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
                    Switch(checked = isReminderEnabled, onCheckedChange = { isReminderEnabled = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Default Delay: ${delayMinutes.toInt()} minutes", style = MaterialTheme.typography.bodySmall)
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
                        onSave(name.trim(), isReminderEnabled, delayMinutes.toInt())
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
fun GroupContactsDialog(
    group: ContactGroupEntity,
    contacts: List<ContactSettingEntity>,
    onDismiss: () -> Unit,
    onToggleContactAssignment: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Contacts for ${group.name}") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (contacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No contacts available")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(contacts, key = { it.contactId }) { contact ->
                            val isAssigned = contact.groupId == group.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleContactAssignment(contact.contactId, !isAssigned) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isAssigned,
                                    onCheckedChange = { checked ->
                                        onToggleContactAssignment(contact.contactId, checked)
                                    }
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
