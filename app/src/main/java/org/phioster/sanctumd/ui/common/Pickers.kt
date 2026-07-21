package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Selectable tab icons; the stored key maps back to a Material icon. */
internal val tabIcons: List<Pair<String, ImageVector>> = listOf(
    "home" to Icons.Filled.Home,
    "movie" to Icons.Filled.Movie,
    "tv" to Icons.Filled.Tv,
    "livetv" to Icons.Filled.LiveTv,
    "music" to Icons.Filled.MusicNote,
    "download" to Icons.Filled.Download,
    "book" to Icons.Filled.MenuBook,
    "star" to Icons.Filled.Star,
    "favorite" to Icons.Filled.Favorite,
    "folder" to Icons.Filled.Folder,
)

internal fun tabIcon(key: String): ImageVector =
    tabIcons.firstOrNull { it.first == key }?.second ?: Icons.Filled.Home

/** Accent choices shared by card and tab pickers; 0 = "use the default" (service/tab colour). */
internal val accentPalette = listOf(0L, 0xFF00FF41L, 0xFF35D07AL, 0xFF00A4DCL, 0xFFFFC230L, 0xFFE66000L, 0xFF818CF8L, 0xFFEC4899L, 0xFF8B5CF6L, 0xFFE5534BL)

@Composable
internal fun AccentPickerRow(selected: Long, defaultColor: Color, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        accentPalette.forEach { c ->
            val col = if (c == 0L) defaultColor else Color(c)
            val sel = selected == c
            Box(
                Modifier.padding(end = 10.dp).size(34.dp).clip(RoundedCornerShape(50))
                    .background(col).border(if (sel) 3.dp else 0.dp, MatrixGreen, RoundedCornerShape(50))
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
internal fun InlineSearchBar(onSearch: (String) -> Unit) {
    var term by remember { mutableStateOf("") }
    OutlinedTextField(
        value = term,
        onValueChange = { term = it },
        placeholder = { Text("search all services…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MatrixGreen.copy(alpha = 0.6f)) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, color = MatrixGreen),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (term.isNotBlank()) onSearch(term.trim()) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen.copy(alpha = 0.3f), cursorColor = MatrixGreen,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun IconPickerGrid(selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        tabIcons.forEach { (key, icon) ->
            val sel = key == selected
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) MatrixGreen else Surface)
                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = key, tint = if (sel) Black else MatrixGreen)
            }
        }
    }
}

internal data class OnboardPage(val icon: String, val title: String, val body: String)

/** First-run welcome flow: a few swipeable pages, then "get started" opens Add-service. */
