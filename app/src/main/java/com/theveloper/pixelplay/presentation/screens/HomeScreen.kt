package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.components.DailyMixSection
import com.theveloper.pixelplay.presentation.components.HomeGradientTopBar
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.StatsOverviewCard
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

// Modern HomeScreen with Featured hero card and Recently played row.
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val allSongs by playerViewModel.allSongsFlow.collectAsState(initial = emptyList())
    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsState()
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsState()

    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, allSongs) {
        when {
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            else -> allSongs.toImmutableList()
        }
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || isBenchmarkMode
    }

    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsState(initial = null)

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val isShuffleEnabled = stablePlayerState.isShuffleEnabled

    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val betaSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val weeklyStats by statsViewModel.weeklyOverview.collectAsState()

    // Recently-played proxy: top songs from this week, fallback to yourMixSongs.
    val recentlyPlayed: ImmutableList<Song> = remember(weeklyStats, yourMixSongs, allSongs) {
        val statTopIds = weeklyStats?.topSongs?.map { it.songId }.orEmpty()
        val byStats = if (statTopIds.isNotEmpty()) {
            val byId = (allSongs + yourMixSongs).distinctBy { it.id }.associateBy { it.id }
            statTopIds.mapNotNull { byId[it] }
        } else emptyList()
        val combined = (byStats + yourMixSongs).distinctBy { it.id }.take(6)
        combined.toImmutableList()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                HomeGradientTopBar(
                    onNavigationIconClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onMoreOptionsClick = {
                        showChangelogBottomSheet = true
                    },
                    onBetaClick = {
                        showBetaInfoBottomSheet = true
                    },
                    onMenuClick = { /* disabled */ }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                            + 38.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(key = "greeting") {
                    GreetingSection()
                }

                item(key = "featured_hero") {
                    FeaturedHeroCard(
                        songs = yourMixSongs,
                        isShuffleEnabled = isShuffleEnabled,
                        onPlay = {
                            if (yourMixSongs.isNotEmpty()) {
                                playerViewModel.showAndPlaySong(
                                    song = yourMixSongs.first(),
                                    contextSongs = yourMixSongs,
                                    queueName = "Your Mix"
                                )
                            }
                        },
                        onShuffle = {
                            if (yourMixSongs.isNotEmpty()) {
                                playerViewModel.playSongsShuffled(
                                    songsToPlay = yourMixSongs,
                                    queueName = "Your Mix"
                                )
                            }
                        }
                    )
                }

                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "recently_played") {
                        RecentlyPlayedSection(
                            songs = recentlyPlayed,
                            onSongClick = { song ->
                                playerViewModel.showAndPlaySong(song, recentlyPlayed, "Recently played")
                            },
                            onSeeAll = {
                                navController.navigate(Screen.Stats.route)
                            }
                        )
                    }
                }

                if (dailyMixSongs.isNotEmpty()) {
                    item(key = "daily_mix_section") {
                        DailyMixSection(
                            songs = dailyMixSongs.take(4).toImmutableList(),
                            onClickOpen = {
                                navController.navigate(Screen.DailyMixScreen.route)
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }

                item(key = "listening_stats_preview") {
                    StatsOverviewCard(
                        summary = weeklyStats,
                        onClick = { navController.navigate(Screen.Stats.route) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(170.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        )
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigate(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
    if (showChangelogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogBottomSheet = false },
            sheetState = sheetState
        ) {
            ChangelogBottomSheet()
        }
    }
    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false },
            sheetState = betaSheetState
        ) {
            BetaInfoBottomSheet()
        }
    }
}

@Composable
private fun GreetingSection() {
    val hour = remember {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Welcome back",
            style = ExpTitleTypography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeaturedHeroCard(
    songs: ImmutableList<Song>,
    isShuffleEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val heroArtUri = songs.firstOrNull()?.albumArtUriString
    val trackCount = songs.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(280.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Decorative blurred sphere using the first song's artwork.
            if (heroArtUri != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val sphereSize = maxWidth * 0.85f
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = sphereSize * 0.30f, y = (-12).dp)
                            .size(sphereSize)
                            .clip(CircleShape)
                            .blur(28.dp)
                    ) {
                        SmartImage(
                            model = heroArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            contentScale = ContentScale.Crop,
                            targetSize = coil.size.Size(900, 900)
                        )
                    }
                    // Soft gradient overlay so the title remains legible over the sphere.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    0.0f to colors.primaryContainer,
                                    0.55f to colors.primaryContainer.copy(alpha = 0.55f),
                                    1.0f to Color.Transparent
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "FEATURED · DAILY MIX",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Your\nMix",
                        style = ExpTitleTypography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        lineHeight = 56.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val subtitle = if (trackCount > 0) {
                        "$trackCount tracks · refreshed today"
                    } else {
                        "Refreshed today"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onPlay,
                        shape = CircleShape,
                        color = colors.onPrimaryContainer,
                        contentColor = colors.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Play",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(
                        onClick = onShuffle,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = colors.onPrimaryContainer.copy(alpha = 0.18f),
                            contentColor = colors.onPrimaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_shuffle_24),
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) colors.tertiary else colors.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedSection(
    songs: ImmutableList<Song>,
    onSongClick: (Song) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recently played",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSeeAll) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = songs, key = { it.id }) { song ->
                RecentlyPlayedTile(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun RecentlyPlayedTile(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(20.dp),
                targetSize = coil.size.Size(420, 420)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = song.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// SongListItem retained for callers in the rest of the app.
@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = albumArtUrl,
                    contentDescription = "Carátula de $title",
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier
                        .weight(0.1f)
                        .padding(start = 8.dp)
                        .size(width = 18.dp, height = 16.dp),
                    color = colors.primary,
                    isPlaying = isPlaying
                )
            }
        }
    }
}

// Wrapper Composable for SongListItemFavs to isolate state observation
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.displayArtist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = stablePlayerState.isPlaying,
        isCurrentSong = song.id == stablePlayerState.currentSong?.id,
        onClick = onClick
    )
}
