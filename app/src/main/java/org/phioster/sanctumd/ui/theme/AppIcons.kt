package org.phioster.sanctumd.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's icon vocabulary — one name per meaning, one drawing per name.
 *
 * Screens used to spell their symbols out themselves, some as vector icons and some as characters
 * in a label (`"⬇  download"`). The mono font has no glyph for several of those, so Android
 * substituted a different font per character and the same action ended up with two different
 * arrows two screens apart. Everything that draws an action now asks here, the way colours ask
 * [MatrixGreen] and the theme.
 *
 * The names say what the icon *means*, not what it looks like: [Monitored], not "bookmark". A
 * drawing can then be swapped in one place without a screen reading wrong afterwards.
 */
internal object AppIcons {
    // ── Playback ──
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val Next: ImageVector = Icons.Filled.SkipNext
    val Previous: ImageVector = Icons.Filled.SkipPrevious
    val Shuffle: ImageVector = Icons.Filled.Shuffle
    val Repeat: ImageVector = Icons.Filled.Repeat
    val RepeatOne: ImageVector = Icons.Filled.RepeatOne
    val Resume: ImageVector = Icons.Filled.Replay
    val Subtitles: ImageVector = Icons.Filled.Subtitles
    val Audio: ImageVector = Icons.Filled.VolumeUp
    val Quality: ImageVector = Icons.Filled.HighQuality

    // ── Library actions ──
    val Download: ImageVector = Icons.Filled.Download
    val Add: ImageVector = Icons.Filled.Add
    val Search: ImageVector = Icons.Filled.Search
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Edit: ImageVector = Icons.Filled.Edit
    val Open: ImageVector = Icons.Filled.OpenInNew
    val Folder: ImageVector = Icons.Filled.Folder

    // ── State ──
    val Cancel: ImageVector = Icons.Filled.Close
    val Done: ImageVector = Icons.Filled.Check
    val Failed: ImageVector = Icons.Filled.Warning
    val Favorite: ImageVector = Icons.Filled.Favorite
    val NotFavorite: ImageVector = Icons.Filled.FavoriteBorder
    val Monitored: ImageVector = Icons.Filled.Bookmark
    val NotMonitored: ImageVector = Icons.Outlined.BookmarkBorder
    val Watched: ImageVector = Icons.Filled.Visibility
    val Unwatched: ImageVector = Icons.Filled.VisibilityOff

    // ── Navigation ──
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val More: ImageVector = Icons.Filled.MoreVert
    val Settings: ImageVector = Icons.Filled.Settings
    val Info: ImageVector = Icons.Filled.Info
}
