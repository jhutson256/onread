package com.example.onread.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun OnboardingDialog(
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Welcome to ONread")
        },
        text = {
            Text("Would you like to automatically enable text reminders for all of your contacts by default?")
        },
        confirmButton = {
            TextButton(onClick = onEnableAll) {
                Text("Yes, Enable for All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDisableAll) {
                Text("No, Disable for All")
            }
        }
    )
}
