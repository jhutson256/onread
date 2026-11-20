package com.example.onread.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.onread.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

/**
 * ONBOARDING TUTORIAL (Style A: swipe-through pages)
 *
 * Shown the first time someone opens Onread, and again whenever they tap
 * "Replay Tutorial" in Settings.
 *
 * isFirstRun = true  -> includes the "Who should Onread watch?" page.
 * isFirstRun = false -> (replay) skips that page, because answering it again
 *                       would reset every contact's reminder setting.
 *
 * onFinish(enableAllContacts) is called when the person finishes or skips.
 */

// The pages, in order. Add, remove or reorder pages here.
private enum class OnboardingPage {
    WELCOME, HOW_IT_WORKS, PERMISSIONS, WHO_TO_WATCH, ALL_SET
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    isFirstRun: Boolean,
    onFinish: (enableAllContacts: Boolean) -> Unit
) {
    val pages = remember(isFirstRun) {
        OnboardingPage.entries.filter { isFirstRun || it != OnboardingPage.WHO_TO_WATCH }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Answer from the "Who should Onread watch?" page (first run only).
    var enableAllContacts by remember { mutableStateOf(true) }

    // ===== Permission status (same checks as the Settings screen) =====
    val runtimePermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Shows the friendly "Onread needs this" message.
    var showPermissionWarning by remember { mutableStateOf(false) }

    val permissionsState = rememberMultiplePermissionsState(runtimePermissions) { results ->
        // Called after the Android permission popups close.
        if (results.values.any { granted -> !granted }) {
            showPermissionWarning = true
        }
    }

    val alarmManager = remember {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }
    fun checkExactAlarm(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && alarmManager != null) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    fun checkNotificationListener(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    var exactAlarmGranted by remember { mutableStateOf(checkExactAlarm()) }
    var notificationListenerGranted by remember { mutableStateOf(checkNotificationListener()) }

    // Re-check when the person comes back from a system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = checkExactAlarm()
                notificationListenerGranted = checkNotificationListener()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun isGranted(permission: String): Boolean =
        permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true

    val allPermissionsGranted =
        permissionsState.allPermissionsGranted && exactAlarmGranted && notificationListenerGranted

    // Hide the warning once everything is allowed.
    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) showPermissionWarning = false
    }

    // ===== Navigation helpers =====
    val currentPage = pages[pagerState.currentPage]
    val isLastPage = pagerState.currentPage == pages.lastIndex

    fun goToPage(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    fun onNextClick() {
        when {
            // Leaving the permissions page with something missing:
            // the first tap explains why it matters, the second tap continues.
            currentPage == OnboardingPage.PERMISSIONS &&
                    !allPermissionsGranted && !showPermissionWarning -> {
                showPermissionWarning = true
            }
            isLastPage -> onFinish(enableAllContacts)
            else -> goToPage(pagerState.currentPage + 1)
        }
    }

    // Phone back button goes to the previous page instead of closing the app.
    BackHandler(enabled = pagerState.currentPage > 0) {
        goToPage(pagerState.currentPage - 1)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // ----- Top row: Skip -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastPage) {
                    TextButton(onClick = { onFinish(enableAllContacts) }) {
                        Text("Skip")
                    }
                }
            }

            // ----- The swipeable pages -----
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (pages[pageIndex]) {
                        OnboardingPage.WELCOME -> WelcomePage()
                        OnboardingPage.HOW_IT_WORKS -> HowItWorksPage()
                        OnboardingPage.PERMISSIONS -> PermissionsPage(
                            contactsGranted = isGranted(Manifest.permission.READ_CONTACTS),
                            smsGranted = isGranted(Manifest.permission.READ_SMS),
                            notificationsGranted =
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                        isGranted(Manifest.permission.POST_NOTIFICATIONS),
                            exactAlarmGranted = exactAlarmGranted,
                            notificationListenerGranted = notificationListenerGranted,
                            showWarning = showPermissionWarning && !allPermissionsGranted,
                            onAllowClick = {
                                if (!permissionsState.allPermissionsGranted) {
                                    permissionsState.launchMultiplePermissionRequest()
                                } else if (!exactAlarmGranted &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                ) {
                                    openSettingsScreen(
                                        context,
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } else if (!notificationListenerGranted) {
                                    openSettingsScreen(
                                        context,
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                }
                            },
                            onOpenAppSettingsClick = {
                                openSettingsScreen(
                                    context,
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        )
                        OnboardingPage.WHO_TO_WATCH -> WhoToWatchPage(
                            enableAllContacts = enableAllContacts,
                            onChoiceChange = { enableAllContacts = it }
                        )
                        OnboardingPage.ALL_SET -> AllSetPage()
                    }
                }
            }

            // ----- Page dots -----
            PageDots(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            )

            // ----- Bottom row: Back / Next -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = { goToPage(pagerState.currentPage - 1) }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(onClick = { onNextClick() }) {
                    Text(
                        when {
                            isLastPage -> "Get Started"
                            currentPage == OnboardingPage.PERMISSIONS &&
                                    !allPermissionsGranted && showPermissionWarning -> "Continue Anyway"
                            else -> "Next"
                        }
                    )
                }
            }
        }
    }
}

// =====================================================================
// PAGES
// =====================================================================

@Composable
private fun WelcomePage() {
    Spacer(modifier = Modifier.height(48.dp))
    Image(
        painter = painterResource(id = R.drawable.onread_logo_transparent),
        contentDescription = "ONread Logo",
        modifier = Modifier.height(96.dp),
        contentScale = ContentScale.Fit
    )
    Spacer(modifier = Modifier.height(32.dp))
    PageTitle("Never leave someone on read again")
    Spacer(modifier = Modifier.height(12.dp))
    PageBody(
        "Onread watches for texts from the people you choose and reminds you to reply. " +
                "When you answer, the reminder goes away on its own."
    )
}

@Composable
private fun HowItWorksPage() {
    PageTitle("How it works")
    Spacer(modifier = Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepCard(
            number = 1,
            title = "Pick your people",
            description = "Choose the contacts and groups you want Onread to keep an eye on."
        )
        StepCard(
            number = 2,
            title = "Get a reminder",
            description = "When they text you, Onread reminds you to reply after the delay or time you set."
        )
        StepCard(
            number = 3,
            title = "Reply and relax",
            description = "As soon as you answer, Onread notices and cancels the reminder for you."
        )
    }
}

@Composable
private fun PermissionsPage(
    contactsGranted: Boolean,
    smsGranted: Boolean,
    notificationsGranted: Boolean,
    exactAlarmGranted: Boolean,
    notificationListenerGranted: Boolean,
    showWarning: Boolean,
    onAllowClick: () -> Unit,
    onOpenAppSettingsClick: () -> Unit
) {
    PageTitle("Onread needs a few permissions")
    Spacer(modifier = Modifier.height(8.dp))
    PageBody("Here's why we ask for each one.")
    Spacer(modifier = Modifier.height(24.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            PermissionRow(
                name = "Contacts",
                reason = "So you can choose who Onread watches.",
                granted = contactsGranted
            )
            PermissionRow(
                name = "Text messages (SMS)",
                reason = "So Onread knows when someone texts you, and when you reply.",
                granted = smsGranted
            )
            PermissionRow(
                name = "Notifications",
                reason = "So Onread can send your reply reminders.",
                granted = notificationsGranted
            )
            PermissionRow(
                name = "Exact alarms",
                reason = "So reminders arrive right on time.",
                granted = exactAlarmGranted
            )
            PermissionRow(
                name = "Notification access",
                reason = "So Onread can also catch RCS chat messages.",
                granted = notificationListenerGranted
            )

            val allGranted = contactsGranted && smsGranted && notificationsGranted &&
                    exactAlarmGranted && notificationListenerGranted

            if (!allGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAllowClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            !contactsGranted || !smsGranted || !notificationsGranted -> "Allow Access"
                            !exactAlarmGranted -> "Allow Exact Alarms"
                            else -> "Turn On Notification Access"
                        }
                    )
                }
                if (contactsGranted && smsGranted && notificationsGranted &&
                    exactAlarmGranted && !notificationListenerGranted
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "In the list that opens, find Onread and switch it on, then come back here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ----- Friendly message when something essential is missing -----
    if (showWarning) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Onread can't work without these",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "These permissions are essential. Without them, Onread can't notice " +
                            "new texts or remind you to reply. You can turn them on now, or anytime " +
                            "later in Settings.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                // If someone said "Don't allow" twice, Android stops showing the popup,
                // so this button sends them to the app's system settings page instead.
                TextButton(onClick = onOpenAppSettingsClick) {
                    Text("Open App Settings")
                }
            }
        }
    }
}

@Composable
private fun WhoToWatchPage(
    enableAllContacts: Boolean,
    onChoiceChange: (Boolean) -> Unit
) {
    PageTitle("Who should Onread watch?")
    Spacer(modifier = Modifier.height(8.dp))
    PageBody("You can change this for any contact later.")
    Spacer(modifier = Modifier.height(24.dp))

    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChoiceCard(
            title = "All my contacts",
            description = "Turn on reminders for everyone. You can switch people off in Contacts.",
            selected = enableAllContacts,
            onClick = { onChoiceChange(true) }
        )
        ChoiceCard(
            title = "I'll choose",
            description = "Start with reminders off, then turn them on for the people you pick.",
            selected = !enableAllContacts,
            onClick = { onChoiceChange(false) }
        )
    }
}

@Composable
private fun AllSetPage() {
    PageTitle("You're all set!")
    Spacer(modifier = Modifier.height(8.dp))
    PageBody("Here's where to find everything.")
    Spacer(modifier = Modifier.height(24.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TabTourRow(Icons.Rounded.NotificationsActive, "Reminders", "See and dismiss your active reminders.")
            TabTourRow(Icons.Rounded.ContactPhone, "Contacts", "Turn reminders on or off and set custom delays.")
            TabTourRow(Icons.Rounded.Group, "Groups", "Give a whole group the same reminder settings.")
            TabTourRow(Icons.Rounded.Settings, "Settings", "Default delay, snooze, permissions, and this tutorial.")
        }
    }
}

// =====================================================================
// SMALL BUILDING BLOCKS (styled like the rest of the app)
// =====================================================================

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PageBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StepCard(number: Int, title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(name: String, reason: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Same icons as the "Required Permissions" card in Settings.
        if (granted) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                Icons.Rounded.Error,
                contentDescription = "Not Granted",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TabTourRow(icon: ImageVector, name: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PageDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "Page ${currentPage + 1} of $pageCount"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

// Opens a system settings screen, falling back to the app's settings page.
private fun openSettingsScreen(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}