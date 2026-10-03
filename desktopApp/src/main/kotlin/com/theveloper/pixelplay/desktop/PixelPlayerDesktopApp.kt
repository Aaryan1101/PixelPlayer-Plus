@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.theveloper.pixelplay.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.desktop.model.DesktopDestination
import com.theveloper.pixelplay.desktop.model.DesktopPlaylist
import com.theveloper.pixelplay.desktop.model.DesktopSong
import com.theveloper.pixelplay.desktop.model.ListeningHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val PixelPurple = Color(0xFFA970FF)
private val PixelPink = Color(0xFFE879F9)
private val PixelOrange = Color(0xFFFFA66B)
private val Ink = Color(0xFF050505)
private val DeepPurple = Color(0xFF0D0D0F)
private val Panel = Color(0xFF121214)
private val RaisedPanel = Color(0xFF1A1A1E)
private val SoftText = Color(0xFFA7A7AD)
private val Divider = Color(0xFF27272B)
private val artworkCache = ConcurrentHashMap<String, ImageBitmap>()

private val DesktopColors = darkColorScheme(
    primary = PixelPurple,
    onPrimary = Color(0xFF240631),
    secondary = PixelPink,
    tertiary = PixelOrange,
    background = Ink,
    onBackground = Color.White,
    surface = DeepPurple,
    onSurface = Color.White,
    surfaceVariant = RaisedPanel,
    onSurfaceVariant = SoftText,
    outline = Color(0xFF35353A),
)

private val DesktopTypography = Typography(
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun PixelPlayerDesktopApp(controller: DesktopController) {
    val state by controller.state.collectAsStateWithLifecycle()
    var showCreatePlaylist by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = DesktopColors, typography = DesktopTypography) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val showQueue = maxWidth >= 1000.dp
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Sidebar(
                            state = state,
                            onNavigate = controller::navigate,
                            onSelectPlaylist = controller::selectPlaylist,
                            onCreatePlaylist = { showCreatePlaylist = true },
                            onChooseFolder = {
                                scope.launch {
                                    chooseDirectory()?.let(controller::addLibraryFolder)
                                }
                            },
                        )
                        MainPanel(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)),
                            state = state,
                            controller = controller,
                        )
                        AnimatedVisibility(
                            visible = showQueue,
                            enter = slideInHorizontally { it } + fadeIn(),
                            exit = slideOutHorizontally { it } + fadeOut(),
                        ) {
                            QueuePanel(state, controller)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PlayerBar(state, controller)
            }
        }

        if (showCreatePlaylist) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylist = false },
                onCreate = {
                    controller.createPlaylist(it)
                    showCreatePlaylist = false
                },
            )
        }
    }
}

@Composable
private fun Sidebar(
    state: DesktopUiState,
    onNavigate: (DesktopDestination) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(
        Modifier
            .width(198.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(DeepPurple)
            .border(1.dp, Divider.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            PixelLogo(34.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("PixelPlayer", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("DESKTOP", fontSize = 8.sp, letterSpacing = 1.8.sp, color = PixelPurple)
            }
        }

        Spacer(Modifier.height(18.dp))
        NavigationItem(Icons.Rounded.Home, "Home", state.destination == DesktopDestination.HOME && state.selectedPlaylistId == null) {
            onNavigate(DesktopDestination.HOME)
        }
        NavigationItem(Icons.Rounded.Search, "Search", state.destination == DesktopDestination.SEARCH && state.selectedPlaylistId == null) {
            onNavigate(DesktopDestination.SEARCH)
        }
        NavigationItem(Icons.Rounded.LibraryMusic, "Your Library", state.destination == DesktopDestination.LIBRARY && state.selectedPlaylistId == null) {
            onNavigate(DesktopDestination.LIBRARY)
        }
        NavigationItem(Icons.Rounded.Favorite, "Liked Songs", state.destination == DesktopDestination.LIKED && state.selectedPlaylistId == null) {
            onNavigate(DesktopDestination.LIKED)
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PLAYLISTS", fontSize = 11.sp, letterSpacing = 1.5.sp, color = SoftText, modifier = Modifier.weight(1f))
            IconButton(onClick = onCreatePlaylist, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Add, "Create playlist", modifier = Modifier.size(18.dp))
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(state.playlists, key = DesktopPlaylist::id) { playlist ->
                NavigationItem(
                    icon = Icons.Rounded.QueueMusic,
                    label = playlist.name,
                    selected = state.selectedPlaylistId == playlist.id,
                    compact = true,
                ) { onSelectPlaylist(playlist.id) }
            }
        }

        OutlinedButton(
            onClick = onChooseFolder,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add music folder")
        }
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.10f)
            hovered -> Color.White.copy(alpha = 0.055f)
            else -> Color.Transparent
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else SoftText, modifier = Modifier.size(if (compact) 17.dp else 20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) Color.White else SoftText,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MainPanel(modifier: Modifier, state: DesktopUiState, controller: DesktopController) {
    Column(modifier.background(Panel).border(1.dp, Divider.copy(alpha = 0.7f), RoundedCornerShape(9.dp))) {
        TopBar(state, controller)
        Crossfade(
            targetState = state.selectedPlaylistId to state.destination,
            modifier = Modifier.fillMaxSize(),
        ) { (playlistId, destination) ->
            if (playlistId != null) {
                val playlist = state.playlists.firstOrNull { it.id == playlistId }
                PlaylistContent(playlist, state, controller)
            } else {
                when (destination) {
                    DesktopDestination.HOME -> HomeContent(state, controller)
                    DesktopDestination.SEARCH -> SearchContent(state, controller)
                    DesktopDestination.LIBRARY -> LibraryContent(state, controller)
                    DesktopDestination.LIKED -> LikedContent(state, controller)
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: DesktopUiState, controller: DesktopController) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.isScanning) "Indexing your sound…" else state.statusMessage ?: "Your music, your space",
            color = SoftText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = controller::refreshLibrary) {
            Icon(
                Icons.Rounded.Refresh,
                "Refresh library",
                tint = if (state.isScanning) PixelPurple else SoftText,
                modifier = Modifier.rotate(if (state.isScanning) 28f else 0f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PixelPurple, PixelPink))),
            contentAlignment = Alignment.Center,
        ) {
            Text("P", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeContent(state: DesktopUiState, controller: DesktopController) {
    val recentlyPlayed = state.listeningHistory.map(ListeningHistoryEntry::song).take(10)
    val hasPersonalContent = recentlyPlayed.isNotEmpty() || state.recommendedSongs.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { HeroCard(state, controller) }
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeading("Recently played", "Pick up exactly where you left off")
                Spacer(Modifier.height(12.dp))
                SongShelf(recentlyPlayed, state, controller)
            }
        }
        if (state.recommendedSongs.isNotEmpty()) {
            item {
                val seed = state.player.currentSong?.title ?: recentlyPlayed.firstOrNull()?.title
                SectionHeading("Keep the session going", seed?.let { "Radio shaped around $it" } ?: "Your evolving radio")
                Spacer(Modifier.height(12.dp))
                SongShelf(state.recommendedSongs, state, controller)
            }
        }
        if (state.listeningHistory.isNotEmpty()) {
            item {
                SectionHeading("Artists in your rotation", "Built from what you actually play")
                Spacer(Modifier.height(12.dp))
                HistoryArtistGrid(state, controller)
            }
        }
        state.homeShelves.forEach { (title, songs) ->
            item(key = "home-$title") {
                SectionHeading(title, shelfSubtitle(title))
                Spacer(Modifier.height(12.dp))
                SongShelf(songs, state, controller)
            }
        }
        if (state.songs.isNotEmpty()) {
            item {
                SectionHeading("From your library", "Music stored on this computer")
                Spacer(Modifier.height(13.dp))
                SongShelf(state.songs.sortedByDescending { it.path }.take(10), state, controller)
            }
        }
        if (!hasPersonalContent && state.homeShelves.isEmpty() && state.songs.isEmpty()) {
            item { EmptyLibraryCard(controller) }
        }
        if (state.isLoadingHome && state.homeShelves.isEmpty()) {
            item { HomeLoadingCard() }
        }
    }
}

@Composable
private fun SongShelf(songs: List<DesktopSong>, state: DesktopUiState, controller: DesktopController) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = DesktopSong::id) { song ->
            AlbumCard(song, state, controller) {
                controller.playSong(song, if (song.isOnline) listOf(song) else songs)
            }
        }
    }
}

@Composable
private fun HistoryArtistGrid(state: DesktopUiState, controller: DesktopController) {
    val artists = state.listeningHistory
        .groupBy { it.song.displayArtist }
        .entries
        .sortedByDescending { (_, entries) -> entries.sumOf { it.playCount } }
        .take(4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        artists.forEachIndexed { index, (artist, entries) ->
            val songs = entries.map(ListeningHistoryEntry::song).distinctBy(DesktopSong::id)
            Row(
                Modifier.weight(1f).height(66.dp).clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { songs.firstOrNull()?.let { controller.playSong(it, songs) } }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkTile(songs.firstOrNull(), 48.dp, index)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("Artist radio", color = SoftText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingCard() {
    Box(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(DeepPurple),
        contentAlignment = Alignment.Center,
    ) {
        Text("Building your home mixes…", color = SoftText)
    }
}

private fun shelfSubtitle(title: String): String = when {
    title.startsWith("More like") -> "Songs around an artist you return to"
    title == "Trending now" -> "Popular music to start a new session"
    title == "Made for your mood" -> "A calmer lane for the next hour"
    title == "Focus flow" -> "Low-distraction music for deep work"
    title == "Fresh pop" -> "Current pop picks and new releases"
    else -> "Selected for this listening session"
}

@Composable
private fun HeroCard(state: DesktopUiState, controller: DesktopController) {
    val transition = rememberInfiniteTransition()
    val drift by transition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(7_000), AnimationRepeatMode.Reverse),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF382051), Color(0xFF17121D), Color(0xFF0B0B0D)),
                    start = Offset.Zero,
                    end = Offset(1_000f, 600f),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize().alpha(0.6f)) {
            drawCircle(PixelPink.copy(alpha = 0.22f), radius = size.minDimension * 0.36f, center = Offset(size.width * drift, size.height * 0.1f))
            drawCircle(PixelPurple.copy(alpha = 0.25f), radius = size.minDimension * 0.45f, center = Offset(size.width * 0.84f, size.height * 0.9f))
        }
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 30.dp, end = 230.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("PIXELPLAYER DESKTOP", color = PixelPurple, fontSize = 10.sp, letterSpacing = 2.0.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Sound that stays\nout of your way.", fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text("Local music and online radio in one calm listening space.", color = SoftText, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val firstSuggestion = state.listeningHistory.firstOrNull()?.song
                        ?: state.homeShelves.values.firstNotNullOfOrNull { it.firstOrNull() }
                        ?: state.songs.firstOrNull()
                    state.player.currentSong?.let { controller.togglePlayPause() }
                        ?: firstSuggestion?.let { controller.playSong(it, listOf(it)) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF281033)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(if (state.player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(7.dp))
                Text(if (state.player.currentSong == null) "Start listening" else "Continue listening", fontWeight = FontWeight.SemiBold)
            }
        }
        PixelDisc(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp).size(136.dp),
            isPlaying = state.player.isPlaying,
        )
    }
}

@Composable
private fun QuickMixGrid(state: DesktopUiState, controller: DesktopController) {
    val groups = state.songs.groupBy { it.displayArtist }.entries.sortedByDescending { it.value.size }.take(4)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, (artist, songs) ->
            val colors = cardColors(index)
            Row(
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .border(1.dp, Divider.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .clickable { songs.firstOrNull()?.let { controller.playSong(it, songs) } }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkTile(songs.first(), 48.dp, index)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${songs.size} tracks", color = SoftText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchContent(state: DesktopUiState, controller: DesktopController) {
    val query = state.query.trim()
    val localResults = remember(state.songs, query) {
        if (query.isEmpty()) emptyList() else state.songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("Search", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = controller::updateQuery,
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Songs, artists, or albums") },
        )
        Spacer(Modifier.height(20.dp))
        when {
            query.isEmpty() -> BrowseArtists(state, controller)
            else -> SearchResults(localResults, state, controller, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchResults(
    localResults: List<DesktopSong>,
    state: DesktopUiState,
    controller: DesktopController,
    modifier: Modifier = Modifier,
) {
    val hasNothing = localResults.isEmpty() && state.onlineResults.isEmpty() && !state.isOnlineSearching
    if (hasNothing) {
        EmptyMessage(
            if (state.onlineError == null) "No music matched “${state.query.trim()}”" else "Online search is unavailable",
            state.onlineError ?: "Try another title, artist, or album.",
        )
        return
    }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (localResults.isNotEmpty()) {
            item {
                SearchSectionLabel("From your library", "${localResults.size} tracks")
            }
            itemsIndexed(localResults, key = { _, song -> "local-${song.id}" }) { index, song ->
                SongRow(index, song, localResults, state, controller)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        item {
            SearchSectionLabel(
                "Online",
                when {
                    state.isOnlineSearching -> "Searching…"
                    state.onlineResults.isNotEmpty() -> "${state.onlineResults.size} results"
                    else -> "No results"
                },
            )
        }
        if (state.isOnlineSearching) {
            item { Text("Finding playable music…", color = SoftText, modifier = Modifier.padding(vertical = 22.dp)) }
        } else {
            itemsIndexed(state.onlineResults, key = { _, song -> "online-${song.id}" }) { index, song ->
                SongRow(index, song, state.onlineResults, state, controller)
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = SoftText, fontSize = 11.sp)
    }
}

@Composable
private fun BrowseArtists(state: DesktopUiState, controller: DesktopController) {
    val artists = state.songs.groupBy { it.displayArtist }.entries.sortedByDescending { it.value.size }.take(12)
    Column {
        SectionHeading("Browse your artists", "Start with a familiar voice")
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(artists, key = { _, item -> item.key }) { index, (artist, songs) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                        songs.firstOrNull()?.let { controller.playSong(it, songs) }
                    }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkTile(songs.first(), 48.dp, index)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist, fontWeight = FontWeight.SemiBold)
                        Text("${songs.size} songs", color = SoftText, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.PlayArrow, null, tint = PixelPurple)
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(state: DesktopUiState, controller: DesktopController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Your Library", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${state.songs.size} songs across ${state.libraryFolders.size} folders", color = SoftText)
            }
            IconButton(onClick = controller::refreshLibrary) { Icon(Icons.Rounded.Refresh, "Refresh") }
        }
        Spacer(Modifier.height(18.dp))
        SongList(state.songs, state, controller, Modifier.weight(1f))
    }
}

@Composable
private fun LikedContent(state: DesktopUiState, controller: DesktopController) {
    val liked = (state.favoriteSongs + state.songs.filter { it.path in state.favoritePaths })
        .filter { it.path in state.favoritePaths }
        .distinctBy(DesktopSong::path)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        CollectionHeader(
            eyebrow = "COLLECTION",
            title = "Liked Songs",
            subtitle = "${liked.size} songs you kept close",
            colors = listOf(Color(0xFF8B3FD1), PixelPink),
            onPlay = { liked.firstOrNull()?.let { controller.playSong(it, liked) } },
        )
        Spacer(Modifier.height(18.dp))
        if (liked.isEmpty()) {
            EmptyMessage("Nothing liked yet", "Tap the heart beside a song and it will appear here.")
        } else {
            SongList(liked, state, controller, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlaylistContent(playlist: DesktopPlaylist?, state: DesktopUiState, controller: DesktopController) {
    val songs = playlist?.let(controller::songsForPlaylist).orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        CollectionHeader(
            eyebrow = "PLAYLIST",
            title = playlist?.name ?: "Playlist",
            subtitle = "${songs.size} songs",
            colors = listOf(Color(0xFF3E62C7), PixelPurple),
            onPlay = { songs.firstOrNull()?.let { controller.playSong(it, songs) } },
        )
        Spacer(Modifier.height(18.dp))
        if (songs.isEmpty()) {
            EmptyMessage("This playlist is ready for its first song", "Use a song’s menu to add it here.")
        } else {
            SongList(songs, state, controller, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CollectionHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    colors: List<Color>,
    onPlay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(colors.mapIndexed { index, color -> if (index == 0) color.copy(alpha = 0.72f) else color.copy(alpha = 0.42f) })).padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Favorite, null, modifier = Modifier.size(54.dp), tint = Color.White) }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(eyebrow, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.75f))
        }
        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF24102E)),
        ) { Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun SongList(
    songs: List<DesktopSong>,
    state: DesktopUiState,
    controller: DesktopController,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongRow(index, song, songs, state, controller)
        }
    }
}

@Composable
private fun SongRow(
    index: Int,
    song: DesktopSong,
    source: List<DesktopSong>,
    state: DesktopUiState,
    controller: DesktopController,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isCurrent = state.player.currentSong?.id == song.id
    val rowColor by animateColorAsState(
        when {
            isCurrent -> PixelPurple.copy(alpha = 0.10f)
            hovered -> Color.White.copy(alpha = 0.045f)
            else -> Color.Transparent
        },
    )
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(rowColor)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { controller.playSong(song, source) },
                onDoubleClick = { controller.playSong(song, source) },
            )
            .height(58.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrent && state.player.isPlaying) PlayingBars() else Text("${index + 1}", color = SoftText, fontSize = 12.sp)
        }
        ArtworkTile(song, 42.dp, index)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1.25f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) PixelPurple else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = SoftText, fontSize = 11.sp)
        }
        Text(song.displayAlbum, modifier = Modifier.weight(0.8f), color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        IconButton(onClick = { controller.toggleFavorite(song) }, modifier = Modifier.size(34.dp)) {
            Icon(
                if (song.path in state.favoritePaths) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Favorite",
                tint = if (song.path in state.favoritePaths) PixelPink else SoftText,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(formatDuration(song.durationMs), color = SoftText, fontSize = 11.sp, modifier = Modifier.width(43.dp))
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.MoreVert, "More", tint = SoftText, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (state.playlists.isEmpty()) {
                    DropdownMenuItem(text = { Text("Create a playlist first") }, onClick = { menuOpen = false }, enabled = false)
                } else {
                    state.playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to ${playlist.name}") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                            onClick = {
                                controller.addToPlaylist(playlist.id, song)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePanel(state: DesktopUiState, controller: DesktopController) {
    Column(
        Modifier.width(238.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(DeepPurple)
            .border(1.dp, Divider.copy(alpha = 0.72f), RoundedCornerShape(9.dp)).padding(horizontal = 13.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Now playing", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Your current session", color = SoftText, fontSize = 10.sp)
            }
            if (state.player.currentSong != null) {
                IconButton(onClick = { controller.toggleFavorite(state.player.currentSong) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (state.player.currentSong.path in state.favoritePaths) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like current song",
                        tint = if (state.player.currentSong.path in state.favoritePaths) PixelPink else SoftText,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ArtworkTile(state.player.currentSong, 212.dp, state.player.currentSong?.path?.hashCode() ?: 0)
        Spacer(Modifier.height(11.dp))
        Text(
            state.player.currentSong?.title ?: "Choose a song",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            state.player.currentSong?.displayArtist ?: "Your queue will appear here",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = SoftText,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${state.queue.drop((state.currentQueueIndex + 1).coerceAtLeast(0)).size}", color = SoftText, fontSize = 10.sp)
        }
        Spacer(Modifier.height(7.dp))
        val nextSongs = state.queue.drop((state.currentQueueIndex + 1).coerceAtLeast(0))
        if (nextSongs.isEmpty()) {
            EmptyMessage("Your queue is quiet", "Play a song to fill the room.", compact = true)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(nextSongs, key = DesktopSong::id) { song ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { controller.playSong(song, state.queue) }.padding(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkTile(song, 40.dp, state.queue.indexOf(song))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = SoftText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(state: DesktopUiState, controller: DesktopController) {
    val song = state.player.currentSong
    Row(
        Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(9.dp)).background(DeepPurple)
            .border(1.dp, Divider.copy(alpha = 0.82f), RoundedCornerShape(9.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(276.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkTile(song, 50.dp, song?.path?.hashCode() ?: 0)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song?.title ?: "Nothing playing", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(song?.displayArtist ?: "Choose something from your library", maxLines = 1, overflow = TextOverflow.Ellipsis, color = SoftText, fontSize = 11.sp)
            }
            if (song != null) {
                IconButton(onClick = { controller.toggleFavorite(song) }) {
                    Icon(
                        if (song.path in state.favoritePaths) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (song.path in state.favoritePaths) PixelPink else SoftText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Column(Modifier.weight(1f).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = controller::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, null, tint = if (state.isShuffleEnabled) PixelPurple else SoftText, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = controller::playPrevious) { Icon(Icons.Rounded.SkipPrevious, null) }
                FilledIconButton(
                    onClick = controller::togglePlayPause,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF24102E)),
                ) {
                    AnimatedContent(state.player.isPlaying) { playing ->
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(25.dp))
                    }
                }
                IconButton(onClick = { controller.playNext() }) { Icon(Icons.Rounded.SkipNext, null) }
                IconButton(onClick = controller::cycleRepeatMode) {
                    Icon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        null,
                        tint = if (state.repeatMode == RepeatMode.OFF) SoftText else PixelPurple,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(state.player.positionMs), color = SoftText, fontSize = 10.sp, modifier = Modifier.width(40.dp))
                Slider(
                    value = state.player.positionMs.toFloat().coerceAtMost(state.player.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { controller.seekTo(it.toLong()) },
                    valueRange = 0f..state.player.durationMs.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).height(22.dp),
                )
                Text(formatDuration(state.player.durationMs), color = SoftText, fontSize = 10.sp, modifier = Modifier.width(40.dp))
            }
        }

        Row(Modifier.width(196.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VolumeUp, null, tint = SoftText, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Slider(
                value = state.player.volume.toFloat(),
                onValueChange = { controller.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlbumCard(song: DesktopSong, state: DesktopUiState, controller: DesktopController, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.035f else 1f)
    val isPlaying = state.player.currentSong?.id == song.id && state.player.isPlaying
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.width(168.dp).scale(scale).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (hovered) 0.085f else 0.04f))
            .border(1.dp, if (hovered) Divider else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            ArtworkTile(song, 168.dp, song.path.hashCode())
            if (hovered || isPlaying) {
                FilledIconButton(
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PixelPurple, contentColor = Color(0xFF21052D)),
                ) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 11.dp, top = 9.dp, end = 7.dp, bottom = 7.dp)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.displayArtist,
                    color = SoftText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { controller.toggleFavorite(song) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (song.path in state.favoritePaths) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like song",
                        tint = if (song.path in state.favoritePaths) PixelPink else SoftText,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.MoreVert, "Song options", tint = SoftText, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.playlists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Create a playlist first") },
                                onClick = { menuOpen = false },
                                enabled = false,
                            )
                        } else {
                            state.playlists.forEach { playlist ->
                                DropdownMenuItem(
                                    text = { Text("Add to ${playlist.name}") },
                                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                                    onClick = {
                                        controller.addToPlaylist(playlist.id, song)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(song: DesktopSong?, size: androidx.compose.ui.unit.Dp, seed: Int) {
    val colors = cardColors(seed)
    val artwork by produceState<ImageBitmap?>(null, song?.artworkUrl) {
        value = song?.artworkUrl?.let { loadArtwork(it) }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.11f)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork!!,
                contentDescription = song?.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(size * 0.4f))
        }
        if (song != null && artwork == null) {
            Text(
                song.title.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.12f),
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.72f).sp,
            )
        }
    }
}

private suspend fun loadArtwork(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    artworkCache[url] ?: runCatching {
        val youtubeId = Regex("/vi/([^/]+)/").find(url)?.groupValues?.getOrNull(1)
        val candidates = if (youtubeId == null) {
            listOf(url)
        } else {
            listOf(
                "https://i.ytimg.com/vi/$youtubeId/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$youtubeId/sddefault.jpg",
                "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg",
                url,
            ).distinct()
        }
        candidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                val connection = URI(candidate).toURL().openConnection().apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                val bytes = connection.getInputStream().use { it.readBytes() }
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }.getOrNull()?.also { artworkCache[url] = it }
}

@Composable
private fun PixelLogo(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(brush = Brush.linearGradient(listOf(PixelPurple, PixelPink)), radius = this.size.minDimension / 2)
        drawArc(Color.White.copy(alpha = 0.95f), 25f, 285f, false, topLeft = Offset(this.size.width * 0.25f, this.size.height * 0.25f), size = Size(this.size.width * 0.5f, this.size.height * 0.5f), style = Stroke(width = this.size.width * 0.09f, cap = StrokeCap.Round))
        drawCircle(Color.White, radius = this.size.width * 0.08f, center = center)
    }
}

@Composable
private fun PixelDisc(modifier: Modifier, isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition()
    val rotation by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isPlaying) 7_000 else 80_000), AnimationRepeatMode.Restart))
    Canvas(modifier.rotate(rotation)) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF5B2B70), Color(0xFF160E1D), Color(0xFF070509))))
        repeat(5) { index ->
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension * (0.18f + index * 0.07f), style = Stroke(width = 1.3f))
        }
        drawCircle(PixelPink, radius = size.minDimension * 0.13f)
        drawCircle(Color(0xFF160E1D), radius = size.minDimension * 0.032f)
    }
}

@Composable
private fun PlayingBars() {
    val transition = rememberInfiniteTransition()
    val height by transition.animateFloat(0.25f, 0.95f, infiniteRepeatable(tween(520), AnimationRepeatMode.Reverse))
    Canvas(Modifier.size(18.dp)) {
        val widths = size.width / 7f
        repeat(3) { index ->
            val multiplier = if (index == 1) height else 1f - height * 0.45f
            drawRoundRect(PixelPurple, topLeft = Offset(index * widths * 2f + widths * 0.3f, size.height * (1f - multiplier)), size = Size(widths, size.height * multiplier), cornerRadius = androidx.compose.ui.geometry.CornerRadius(widths))
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SoftText, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyLibraryCard(controller: DesktopController) {
    Box(
        Modifier.fillMaxWidth().height(232.dp).clip(RoundedCornerShape(14.dp)).background(DeepPurple).border(1.dp, Divider, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.FolderOpen, null, tint = PixelPurple, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text("Your next track is one search away", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Explore music online, or add a local folder from the sidebar.", color = SoftText)
            Spacer(Modifier.height(15.dp))
            Button(
                onClick = { controller.navigate(DesktopDestination.SEARCH) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Search online")
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String, compact: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = if (compact) 28.dp else 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = PixelPurple.copy(alpha = 0.8f), modifier = Modifier.size(if (compact) 30.dp else 46.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 14.sp else 18.sp)
        Text(subtitle, color = SoftText, fontSize = if (compact) 11.sp else 13.sp)
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun cardColors(seed: Int): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF8237A4), Color(0xFFEF6A9A)),
        listOf(Color(0xFF355CA8), Color(0xFF7E6BEF)),
        listOf(Color(0xFFB84D65), Color(0xFFFF9D76)),
        listOf(Color(0xFF257C75), Color(0xFF68C7A7)),
        listOf(Color(0xFF784521), Color(0xFFE29D51)),
    )
    return palettes[kotlin.math.abs(seed % palettes.size)]
}

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0) return "--:--"
    val totalSeconds = milliseconds / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private suspend fun chooseDirectory(): String? = withContext(Dispatchers.IO) {
    suspendCoroutine { continuation ->
        SwingUtilities.invokeLater {
            val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
                dialogTitle = "Choose your music folder"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            val result = chooser.showOpenDialog(null)
            continuation.resume(if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null)
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateWithLifecycle(): androidx.compose.runtime.State<T> =
    collectAsState()
