package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.JellyLibrary
import org.phioster.sanctumd.model.JellySession
import org.phioster.sanctumd.model.JellyUser
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Who is watching and who is allowed to: the Now Playing and Users tabs.
 *
 * They share a file because they are two views of the same thing, a session belongs to a user,
 * and both are managed from the same place on the server.
 */
internal class JellyfinPeopleState {
    var sessions by mutableStateOf<List<JellySession>?>(null)
    var users by mutableStateOf<List<JellyUser>?>(null)
    var editUser by mutableStateOf<JellyUser?>(null)
    var showCreateUser by mutableStateOf(false)
    var newUserName by mutableStateOf("")
    var newUserPass by mutableStateOf("")
    /** Session id a message is being composed for. */
    var messageFor by mutableStateOf<String?>(null)
    var messageText by mutableStateOf("")

    /** Returns an error message, or null when it worked. */
    suspend fun loadSessions(vm: DashboardViewModel, config: ServiceConfig): String? = guard {
        sessions = vm.jellyfinSessionList(config)
    }

    suspend fun loadUsers(vm: DashboardViewModel, config: ServiceConfig): String? = guard {
        users = vm.jellyfinUserList(config)
    }

    private inline fun guard(body: () -> Unit): String? = try {
        body()
        null
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        t.message ?: "failed"
    }
}

@Composable
internal fun rememberJellyfinPeopleState() = remember { JellyfinPeopleState() }

/** Now Playing: the sessions currently open against this server. */
internal fun LazyListScope.jellyfinSessionsTab(
    st: JellyfinPeopleState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onAct: ((suspend () -> String)) -> Unit,
) {
    val s = st.sessions
    when {
        s == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
        s.isEmpty() -> item { Text("no active sessions", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
        else -> items(s) { sess ->
            JellySessionRow(
                item = sess,
                accent = accent,
                onPlayPause = { onAct { vm.jellyfinControl(config, sess.id, if (sess.paused) "Unpause" else "Pause") } },
                onStop = { onAct { vm.jellyfinControl(config, sess.id, "Stop") } },
                onMessage = { st.messageFor = sess.id; st.messageText = "" },
            )
        }
    }
}

/** Users: the account list, and the entry point for creating one. */
internal fun LazyListScope.jellyfinUsersTab(
    st: JellyfinPeopleState,
    accent: Color,
) {
    val u = st.users
    item {
        Spacer(Modifier.height(8.dp))
        Text(
            "+ new user",
            fontFamily = Mono, color = accent, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clickable { st.newUserName = ""; st.newUserPass = ""; st.showCreateUser = true }.padding(vertical = 6.dp),
        )
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
    when {
        u == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
        u.isEmpty() -> item { Text("no users", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
        else -> items(u) { usr -> JellyUserRow(usr, accent) { st.editUser = usr } }
    }
}

/** Send-message, create-user and edit-user. */
@Composable
internal fun JellyfinPeopleDialogs(
    st: JellyfinPeopleState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    libraries: List<JellyLibrary>?,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    onReloadUsers: suspend () -> Unit,
) {
    st.messageFor?.let { sid ->
        AlertDialog(
            onDismissRequest = { st.messageFor = null },
            containerColor = Surface,
            title = { Text("Send message", fontFamily = Mono, color = MatrixGreen) },
            text = { Field("Message", st.messageText) { st.messageText = it } },
            confirmButton = {
                TextButton(enabled = st.messageText.isNotBlank(), onClick = {
                    val txt = st.messageText; st.messageFor = null
                    scope.launch { onMessage(vm.jellyfinMessage(config, sid, txt)) }
                }) { Text("Send", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { st.messageFor = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (st.showCreateUser) {
        AlertDialog(
            onDismissRequest = { st.showCreateUser = false },
            containerColor = Surface,
            title = { Text("New user", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Username", st.newUserName) { st.newUserName = it }
                    Field("Password (optional)", st.newUserPass, isPassword = true) { st.newUserPass = it }
                }
            },
            confirmButton = {
                TextButton(enabled = st.newUserName.isNotBlank(), onClick = {
                    val n = st.newUserName; val p = st.newUserPass; st.showCreateUser = false
                    scope.launch { onMessage(vm.jellyfinAddUser(config, n, p)); onReloadUsers() }
                }) { Text("Create", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { st.showCreateUser = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    st.editUser?.let { usr ->
        JellyUserDialog(
            user = usr,
            libraries = libraries,
            onDismiss = { st.editUser = null },
            onSave = { admin, disabled, allowDownloads, enableAll, folders ->
                st.editUser = null
                scope.launch {
                    onMessage(vm.jellyfinUpdatePolicy(config, usr.id, admin, disabled, allowDownloads, enableAll, folders))
                    onReloadUsers()
                }
            },
            onResetPassword = { newPw ->
                scope.launch { onMessage(vm.jellyfinResetPassword(config, usr.id, newPw)) }
            },
            onDelete = {
                st.editUser = null
                scope.launch { onMessage(vm.jellyfinRemoveUser(config, usr.id)); onReloadUsers() }
            },
        )
    }
}
