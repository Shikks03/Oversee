package com.example.oversee.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.PairingRepository
import com.example.oversee.data.local.PairingLogic
import com.example.oversee.ui.components.dialogs.OverSeeDialog
import com.example.oversee.ui.components.inputs.OverSeeTextField

/**
 * Parent enters the 6-digit code shown on the new child device, reviews the intent,
 * and approves. On approval the child device finalizes itself.
 *
 * @param onApproved called after the parent approves a valid code (parent should refresh).
 */
@Composable
fun AddChildDialog(onDismiss: () -> Unit, onApproved: () -> Unit) {
    val uid = remember { AuthRepository.getUserId() }

    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<PairingRepository.LookupResult.Valid?>(null) }

    confirming?.let { valid ->
        OverSeeDialog(
            title = "Approve device",
            description = PairingLogic.intentSummary(valid.pairing.intent, valid.pairing.targetName),
            confirmText = "Approve",
            dismissText = "Cancel",
            isDestructive = valid.pairing.intent == PairingRepository.Intent.REPLACE,
            onConfirm = {
                val u = uid ?: return@OverSeeDialog
                busy = true
                PairingRepository.approve(u, valid.code) { ok ->
                    busy = false
                    confirming = null
                    if (ok) onApproved() else error = "Approval failed. Try again."
                }
            },
            onDismiss = { confirming = null }
        )
        return
    }

    OverSeeDialog(
        title = "Add Child Device",
        description = "Enter the 6-digit code shown on the new child device.",
        confirmText = if (busy) "Checking…" else "Continue",
        dismissText = "Cancel",
        onConfirm = {
            val u = uid
            if (u == null) { error = "Not signed in."; return@OverSeeDialog }
            if (code.length != 6 || code.any { !it.isDigit() }) { error = "Enter the full 6-digit code."; return@OverSeeDialog }
            busy = true
            error = null
            PairingRepository.lookup(u, code) { result ->
                busy = false
                when (result) {
                    is PairingRepository.LookupResult.Valid -> confirming = result
                    PairingRepository.LookupResult.NotFound -> error = "No pending device found for that code."
                    PairingRepository.LookupResult.Expired -> error = "That code has expired. Ask the child device to generate a new one."
                    PairingRepository.LookupResult.AlreadyApproved -> error = "That code was already approved."
                }
            }
        },
        onDismiss = onDismiss
    ) {
        Column {
            OverSeeTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                label = "6-Digit Code",
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
