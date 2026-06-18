package com.example.oversee.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.PairingRepository
import com.example.oversee.data.local.PairingLogic
import com.example.oversee.data.remote.FirebaseIncidentManager
import com.example.oversee.ui.components.dialogs.OverSeeDialog
import com.example.oversee.ui.theme.AppTheme
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay

private sealed class PairingStage {
    object Loading : PairingStage()
    object Decision : PairingStage()
    object Secondary : PairingStage()
    object PickChild : PairingStage()
    data class Waiting(val code: String) : PairingStage()
    object Finalizing : PairingStage()
}

/**
 * Drives child onboarding after the "Child" role is picked.
 * @param onPaired navigate to the child dashboard (role + local prefs already set)
 * @param onBackToLogin log out and return to the auth screen
 */
@Composable
fun ChildPairingFlow(onPaired: () -> Unit, onBackToLogin: () -> Unit) {
    val context = LocalContext.current
    val uid = remember { AuthRepository.getUserId() }

    var stage by remember { mutableStateOf<PairingStage>(PairingStage.Loading) }
    var myFid by remember { mutableStateOf<String?>(null) }
    var children by remember { mutableStateOf<List<DeviceRepository.ChildDevice>>(emptyList()) }
    var replaceTarget by remember { mutableStateOf<DeviceRepository.ChildDevice?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun finalize(intent: PairingRepository.Intent, target: DeviceRepository.ChildDevice?) {
        val u = uid; val fid = myFid
        if (u == null || fid == null) { onBackToLogin(); return }
        stage = PairingStage.Finalizing
        val activate = {
            DeviceRepository.setRoleForThisDevice(context, u, fid, "CHILD") { ok ->
                if (!ok) { errorMsg = "Failed to activate device. Try again."; stage = PairingStage.Decision; return@setRoleForThisDevice }
                if (intent == PairingRepository.Intent.REPLACE && target != null) {
                    DeviceRepository.renameChild(u, fid, target.name) { _ -> onPaired() }
                } else {
                    DeviceRepository.renameChild(u, fid, PairingLogic.defaultChildName(children.size)) { _ -> onPaired() }
                }
            }
        }
        // Best-effort wipe of the old child: ignore delete failures (ghost data is a nuisance, not a safety issue) and proceed to activate.
        if (intent == PairingRepository.Intent.REPLACE && target != null) {
            FirebaseIncidentManager.deleteOldChildData(target.fid) { _ ->
                DeviceRepository.removeChildCompletely(u, target.fid) { _ -> activate() }
            }
        } else {
            activate()
        }
    }

    LaunchedEffect(Unit) {
        if (uid == null) { onBackToLogin(); return@LaunchedEffect }
        DeviceRepository.getFid { fid ->
            myFid = fid
            if (fid == null) { errorMsg = "Could not read device ID."; return@getFid }
            DeviceRepository.fetchChildDevices(context, uid) { list ->
                children = list
                if (list.isEmpty()) {
                    finalize(PairingRepository.Intent.ADD, null)
                } else {
                    stage = PairingStage.Decision
                }
            }
        }
    }

    fun startPairing(intent: PairingRepository.Intent, target: DeviceRepository.ChildDevice?) {
        val u = uid; val fid = myFid
        if (u == null || fid == null) return
        PairingRepository.createPending(u, fid, intent, target?.fid, target?.name) { code ->
            if (code == null) { errorMsg = "Could not start pairing. Try again." }
            else stage = PairingStage.Waiting(code)
        }
    }

    when (val s = stage) {
        is PairingStage.Loading, is PairingStage.Finalizing ->
            CenteredProgress(if (s is PairingStage.Finalizing) "Finalizing setup…" else "Loading…")

        is PairingStage.Decision -> DecisionScreen(
            onAddNew = { startPairing(PairingRepository.Intent.ADD, null) },
            onNo = { stage = PairingStage.Secondary }
        )

        is PairingStage.Secondary -> SecondaryScreen(
            onReplace = { stage = PairingStage.PickChild },
            onBackToLogin = onBackToLogin
        )

        is PairingStage.PickChild -> PickChildScreen(
            children = children,
            onPick = { replaceTarget = it },
            onCancel = { stage = PairingStage.Decision }
        )

        is PairingStage.Waiting -> WaitingScreen(
            uid = uid!!,
            code = s.code,
            onApproved = { pairing ->
                PairingRepository.deletePending(uid!!, s.code)
                val target = children.firstOrNull { it.fid == pairing.targetFid }
                finalize(pairing.intent, target)
            },
            onExpiredOrCancel = {
                PairingRepository.deletePending(uid!!, s.code)
                replaceTarget = null
                stage = PairingStage.Decision
            }
        )
    }

    replaceTarget?.let { target ->
        if (stage is PairingStage.PickChild) {
            OverSeeDialog(
                title = "Replace ${target.name}?",
                description = "This device will take over \"${target.name}\". Its existing monitoring logs will be permanently deleted. A code will be generated for parent approval.",
                confirmText = "Replace",
                dismissText = "Cancel",
                isDestructive = true,
                onConfirm = { replaceTarget = null; startPairing(PairingRepository.Intent.REPLACE, target) },
                onDismiss = { replaceTarget = null }
            )
        }
    }

    errorMsg?.let { msg ->
        OverSeeDialog(
            title = "Something went wrong",
            description = msg,
            confirmText = "OK",
            onConfirm = { errorMsg = null },
            onDismiss = { errorMsg = null }
        )
    }
}

@Composable
private fun CenteredProgress(label: String) {
    Box(Modifier.fillMaxSize().background(AppTheme.ChildBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = AppTheme.ChildAccent)
            Text(label, color = AppTheme.ChildTextSecondary)
        }
    }
}

@Composable
private fun DecisionScreen(onAddNew: () -> Unit, onNo: () -> Unit) {
    PairingScaffold(
        title = "Add this device?",
        subtitle = "This account already has at least one child device. Do you want to add this phone as a new child?"
    ) {
        Button(onClick = onAddNew, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.ChildAccent), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.PersonAdd, null); Spacer(Modifier.width(8.dp))
            Text("Yes, add as new child", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onNo) { Text("No", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SecondaryScreen(onReplace: () -> Unit, onBackToLogin: () -> Unit) {
    PairingScaffold(
        title = "What would you like to do?",
        subtitle = "You can replace one of the existing child devices with this phone, or go back to the login screen."
    ) {
        Button(onClick = onReplace, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.ChildAccent), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(8.dp))
            Text("Replace an existing child", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBackToLogin) { Text("Back to login", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PickChildScreen(
    children: List<DeviceRepository.ChildDevice>,
    onPick: (DeviceRepository.ChildDevice) -> Unit,
    onCancel: () -> Unit
) {
    PairingScaffold(
        title = "Which child to replace?",
        subtitle = "This device will take over the selected child. The old logs will be deleted."
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(children) { child ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(child) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(child.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Text("ID: ${child.displayUid ?: DeviceRepository.toDisplayCode(child.fid)}",
                            fontSize = 12.sp, color = AppTheme.ChildTextSecondary)
                    }
                }
            }
        }
        TextButton(onClick = onCancel) { Text("Cancel", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun WaitingScreen(
    uid: String,
    code: String,
    onApproved: (PairingRepository.PendingPairing) -> Unit,
    onExpiredOrCancel: () -> Unit
) {
    var remaining by remember(code) { mutableStateOf(PairingLogic.EXPIRY_MS) }
    var handled by remember(code) { mutableStateOf(false) }

    DisposableEffect(code) {
        val reg: ListenerRegistration = PairingRepository.listenPending(uid, code) { pairing ->
            if (!handled && pairing != null && pairing.status == "APPROVED") {
                handled = true
                onApproved(pairing)
            }
        }
        onDispose { reg.remove() }
    }

    LaunchedEffect(code) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1000
        }
        if (!handled) {
            handled = true
            onExpiredOrCancel()
        }
    }

    val mins = (remaining / 1000) / 60
    val secs = (remaining / 1000) % 60

    PairingScaffold(
        title = "Waiting for parent approval",
        subtitle = "On the parent device, open OverSee and enter this code (Add Child) to approve this device."
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)) {
            Text(code, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                color = AppTheme.ChildAccent, modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp))
        }
        Text("Expires in %d:%02d".format(mins, secs), color = AppTheme.ChildTextSecondary, fontSize = 14.sp)
        TextButton(onClick = { if (!handled) { handled = true; onExpiredOrCancel() } }) { Text("Cancel", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PairingScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.ChildBackground).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, textAlign = TextAlign.Center)
            Text(subtitle, fontSize = 15.sp, color = AppTheme.ChildTextSecondary, textAlign = TextAlign.Center, lineHeight = 22.sp)
            content()
        }
    }
}
