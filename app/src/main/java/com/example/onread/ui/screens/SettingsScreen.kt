package com.example.onread.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onread.ui.viewmodel.SettingsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }

    val colorTheme by viewModel.colorTheme.collectAsState()
    val defaultDelayMinutes by viewModel.defaultDelayMinutes.collectAsState()
    val savedContactsOnly by viewModel.savedContactsOnly.collectAsState()
    val useSpecificTime by viewModel.useSpecificTime.collectAsState()
    val specificTimeHour by viewModel.specificTimeHour.collectAsState()
    val specificTimeMinute by viewModel.specificTimeMinute.collectAsState()

    val permissionsList = remember {
        val list = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add("android.permission.SCHEDULE_EXACT_ALARM")
        }
        list
    }

    val runtimePermissions = remember {
        permissionsList.filter { it != "android.permission.SCHEDULE_EXACT_ALARM" }
    }
    val runtimePermissionsState = rememberMultiplePermissionsState(runtimePermissions)

    var exactAlarmGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (alarmManager != null)) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
            } else {
                true
            }
        )
    }

    var isNotificationListenerGranted by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, alarmManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        exactAlarmGranted = alarmManager.canScheduleExactAlarms()
                    }
                }
                isNotificationListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPermissionGranted: (String) -> Boolean = { perm ->
        when (perm) {
            "android.permission.SCHEDULE_EXACT_ALARM" -> exactAlarmGranted
            else -> runtimePermissionsState.permissions.find { it.permission == perm }?.status?.isGranted == true
        }
    }

    val allPermissionsGranted = permissionsList.all { isPermissionGranted(it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Global Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Appearance Theme Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Appearance Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                                FilterChip(
                                    selected = colorTheme == key,
                                    onClick = { viewModel.updateColorTheme(key) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Reminder Timing Mode Card (Delay vs Specific Time)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use Clock Based Reminders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Trigger reminders at a fixed time of day instead of a delay after receipt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useSpecificTime,
                                onCheckedChange = { viewModel.updateUseSpecificTime(it) }
                            )
                        }

                        if (useSpecificTime) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Target Time",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val displayHour = if (specificTimeHour == 0) 12 else if (specificTimeHour > 12) specificTimeHour - 12 else specificTimeHour
                                    val amPm = if (specificTimeHour >= 12) "PM" else "AM"
                                    val formattedTime = String.format("%d:%02d %s", displayHour, specificTimeMinute, amPm)
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                viewModel.updateSpecificTime(hourOfDay, minute)
                                            },
                                            specificTimeHour,
                                            specificTimeMinute,
                                            false
                                        ).show()
                                    }
                                ) {
                                    Text("Change Time")
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Default Reminder Delay",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$defaultDelayMinutes minutes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = defaultDelayMinutes.toFloat(),
                                onValueChange = { viewModel.updateDefaultDelayMinutes(it.toInt()) },
                                valueRange = 5f..120f,
                                steps = 22
                            )
                        }
                    }
                }
            }

            // Saved Contacts Only Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saved Contacts Only",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Only trigger reminders for contacts saved in your address book.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = savedContactsOnly,
                            onCheckedChange = { viewModel.updateSavedContactsOnly(it) }
                        )
                    }
                }
            }

            // Notification Listener Permission Card (RCS & Messages)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Listener (RCS & Messages)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Allows ONread to capture 100% of RCS text messages from Google Messages and other messaging apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isNotificationListenerGranted) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Notification Access Granted",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Error,
                                    contentDescription = "Notification Access Not Granted",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        if (!isNotificationListenerGranted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val intent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Notification Access")
                            }
                        }
                    }
                }
            }

            // Runtime Permissions Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Required Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        permissionsList.forEach { perm ->
                            val permName = when (perm) {
                                Manifest.permission.READ_CONTACTS -> "Read Contacts"
                                Manifest.permission.READ_SMS -> "Read SMS"
                                Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications"
                                "android.permission.SCHEDULE_EXACT_ALARM" -> "Schedule Exact Alarm"
                                else -> perm
                            }
                            val granted = isPermissionGranted(perm)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(permName, style = MaterialTheme.typography.bodyMedium)
                                if (granted) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Rounded.Error, contentDescription = "Not Granted", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (!allPermissionsGranted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val missingRuntime = runtimePermissionsState.permissions
                                        .asSequence()
                                        .filter { !it.status.isGranted }
                                        .map { it.permission }
                                        .toList()

                                    if (missingRuntime.isNotEmpty() && activity != null) {
                                        ActivityCompat.requestPermissions(activity, missingRuntime.toTypedArray(), 1001)
                                    }

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmGranted) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                            context.startActivity(intent)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant All Permissions")
                            }
                        }
                    }
                }
            }
        }
    }
}
