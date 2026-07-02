package uns.ac.rs.team23.slagalica.views.common

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared avatar color palette, indexed by UserProfile.avatarIndex. */
val AvatarColors = listOf(
    Color(0xFF42A5F5),
    Color(0xFFAB47BC),
    Color(0xFF66BB6A),
    Color(0xFFFF7043),
)

private val Gold = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

/**
 * Avatar circle with an optional gold/silver/bronze frame.
 *
 * @param frameRank 1/2/3 → gold/silver/bronze (player's region placed top-3 last
 *   monthly cycle); any other value → default primary border.
 */
@Composable
fun AvatarWithFrame(
    avatarIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    frameRank: Int = 0,
    profilePictureUrl: String = "",
) {
    val fillColor = AvatarColors[avatarIndex.mod(AvatarColors.size)]
    val frameColor = when (frameRank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> MaterialTheme.colorScheme.primary
    }
    val borderWidth = if (frameRank in 1..3) 4.dp else 3.dp
    val fallback: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Avatar",
            tint = Color.White,
            modifier = Modifier.size(size * 0.75f),
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .border(borderWidth, frameColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ProfilePhotoContent(
            profilePictureUrl = profilePictureUrl,
            fallback = fallback,
        )
    }
}

@Composable
private fun ProfilePhotoContent(
    profilePictureUrl: String,
    fallback: @Composable () -> Unit,
) {
    when {
        profilePictureUrl.startsWith("data:image") -> {
            var bitmap by remember(profilePictureUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(profilePictureUrl) {
                bitmap = withContext(Dispatchers.Default) {
                    decodeDataUri(profilePictureUrl)?.asImageBitmap()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                fallback()
            }
        }
        profilePictureUrl.isNotBlank() -> {
            SubcomposeAsyncImage(
                model = profilePictureUrl,
                contentDescription = "Profile photo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = { fallback() },
                error = { fallback() },
            )
        }
        else -> fallback()
    }
}

private fun decodeDataUri(dataUri: String): android.graphics.Bitmap? {
    val comma = dataUri.indexOf(',')
    if (comma < 0) return null
    val payload = dataUri.substring(comma + 1)
    val bytes = Base64.decode(payload, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
