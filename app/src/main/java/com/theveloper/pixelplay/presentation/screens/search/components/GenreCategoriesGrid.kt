package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Immutable
data class ExploreCategory(
    val id: String,
    val title: String,
    val searchQuery: String,
    val iconRes: Int,
    val startColor: Color,
    val endColor: Color
)

val onlineExploreCategories = listOf(
    ExploreCategory("trending", "Trending", "trending songs latest official audio", R.drawable.rounded_explosion_24, Color(0xFF7B1FA2), Color(0xFF4A148C)),
    ExploreCategory("new_releases", "New releases", "new music releases latest official audio", R.drawable.round_newspaper_24, Color(0xFF00695C), Color(0xFF004D40)),
    ExploreCategory("punjabi", "Punjabi", "latest Punjabi songs official audio", R.drawable.rattle, Color(0xFFE65100), Color(0xFF9F2D00)),
    ExploreCategory("bollywood", "Bollywood", "latest Bollywood songs official audio", R.drawable.star_angle, Color(0xFFC62828), Color(0xFF7F0000)),
    ExploreCategory("hindi", "Hindi hits", "Hindi hit songs official audio", R.drawable.rounded_music_note_24, Color(0xFFAD1457), Color(0xFF78002E)),
    ExploreCategory("pop", "Pop", "pop hit songs official audio", R.drawable.pop_mic, Color(0xFF1565C0), Color(0xFF003C8F)),
    ExploreCategory("hip_hop", "Hip-Hop", "latest hip hop songs official audio", R.drawable.rapper, Color(0xFF4527A0), Color(0xFF23036A)),
    ExploreCategory("romance", "Romance", "romantic songs hits official audio", R.drawable.round_favorite_24, Color(0xFFD81B60), Color(0xFF880E4F)),
    ExploreCategory("chill", "Chill", "chill songs playlist official audio", R.drawable.rounded_surround_sound_24, Color(0xFF00838F), Color(0xFF005662)),
    ExploreCategory("workout", "Workout", "workout songs high energy official audio", R.drawable.rounded_instant_mix_24, Color(0xFFEF6C00), Color(0xFFB53D00)),
    ExploreCategory("party", "Party", "party dance songs hits official audio", R.drawable.rounded_celebration_24, Color(0xFF6A1B9A), Color(0xFF38006B)),
    ExploreCategory("indie", "Indie", "latest indie songs official audio", R.drawable.acoustic_guitar, Color(0xFF2E7D32), Color(0xFF005005))
)

@Composable
fun OnlineExploreGrid(
    onCategoryClick: (ExploreCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val systemNavBarHeight = getNavigationBarHeight()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + NavBarContentHeight + MiniPlayerHeight + systemNavBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Explore",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            )
        }

        items(onlineExploreCategories, key = ExploreCategory::id) { category ->
            ExploreCategoryCard(category = category, onClick = { onCategoryClick(category) })
        }
    }
}

@Composable
fun ExploreCategoryCard(
    category: ExploreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp,
        smoothnessAsPercentTR = 70,
        cornerRadiusTL = 20.dp,
        smoothnessAsPercentTL = 70,
        cornerRadiusBR = 20.dp,
        smoothnessAsPercentBR = 70,
        cornerRadiusBL = 20.dp,
        smoothnessAsPercentBL = 70
    )

    Box(
        modifier = modifier
            .aspectRatio(1.45f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(category.startColor, category.endColor)))
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(category.iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier
                .size(78.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 12.dp)
                .rotate(16f)
        )

        Text(
            text = category.title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp, end = 56.dp)
        )
    }
}
