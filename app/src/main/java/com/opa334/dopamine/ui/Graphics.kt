package com.opa334.dopamine.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opa334.dopamine.R
import kotlinx.coroutines.delay

/** The height DOHeaderView pins the artwork to. */
private val HeaderArtHeight = 40.dp

/**
 * The header, matching DOHeaderView.
 *
 * iOS builds a vertical UIStackView with 2 pt spacing: the artwork at a fixed 40 pt height, then
 * the dark-mode version string in white 14 pt medium, then the credits line in white 60% 14 pt
 * regular. `DOMainViewController` loads `[UIImage imageNamed:@"Dopamine"]` for that artwork, and
 * that asset is a stylised *wordmark* - the letters "Dopamine" - not the ring from the app icon.
 *
 * The theme's `titleShadow` flag adds a shadow behind both text lines. Only ElleKit and Midnight
 * set it in the plist, and it is not decoration: those two backgrounds are light enough that white
 * text without a shadow stops being legible over the photo.
 */
@Composable
fun DopamineHeader(
    subtitle: String,
    credits: String,
    modifier: Modifier = Modifier,
) {
    val palette = dopaminePalette
    val shadow = if (palette.titleShadow) {
        androidx.compose.ui.graphics.Shadow(
            color = Color(0x99000000),
            offset = Offset(0f, 1f),
            blurRadius = 3f,
        )
    } else {
        null
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        /*
         * 40 dp tall and 202.5 dp wide: Dopamine@3x.png is 810x160, an aspect ratio of exactly
         * 5.0625, and iOS pins only the height, so the width follows from the bitmap.
         *
         * The 7 dp downward offset reproduces DOHeaderView's
         * `imageWithAlignmentRectInsets:UIEdgeInsetsMake(7, 0, -7, 0)`. That is not decoration:
         * iOS slides the artwork down by 7 pt while telling the stack it still occupies its
         * original box, so the 2 pt spacing to the version line collapses and the wordmark sits
         * tight against it. `offset` is used rather than padding because Compose rejects negative
         * padding at runtime, and because the intent here is a visual shift that must not change
         * the measured height - which is exactly what alignmentRectInsets does.
         */
        Image(
            painter = painterResource(R.drawable.dopamine_wordmark),
            contentDescription = "Dopamine",
            modifier = Modifier
                .height(HeaderArtHeight)
                .offset(y = 7.dp),
            contentScale = ContentScale.FillHeight,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = subtitle,
            color = DopamineColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            style = if (shadow != null) {
                androidx.compose.ui.text.TextStyle(shadow = shadow)
            } else {
                androidx.compose.ui.text.TextStyle.Default
            },
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = credits,
            color = DopamineColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            style = if (shadow != null) {
                androidx.compose.ui.text.TextStyle(shadow = shadow)
            } else {
                androidx.compose.ui.text.TextStyle.Default
            },
        )
    }
}

/**
 * The Apple silhouette used by the boot sequence.
 *
 * Drawn as a path rather than shipped as an asset: the real logo is a trademark that does not
 * belong in this repository. [lying] rotates it 90 degrees, which is the "sideways Apple" shown
 * when the boot logo is switched off.
 */
@Composable
fun AppleGlyph(
    modifier: Modifier = Modifier,
    color: Color = DopamineColors.TextPrimary,
    size: Dp = 96.dp,
    lying: Boolean = false,
) {
    val rotation = if (lying) -90f else 0f

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.28f)
            cubicTo(w * 0.30f, h * 0.28f, w * 0.14f, h * 0.44f, w * 0.14f, h * 0.64f)
            cubicTo(w * 0.14f, h * 0.86f, w * 0.32f, h * 0.98f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.68f, h * 0.98f, w * 0.86f, h * 0.86f, w * 0.86f, h * 0.64f)
            cubicTo(w * 0.86f, h * 0.44f, w * 0.70f, h * 0.28f, w * 0.50f, h * 0.28f)
            close()
        }
        drawPath(path, color)

        // Bite, on the right: a notch is what makes the shape unmistakable at small sizes.
        // Punched out with BlendMode.Clear is not available on an opaque background, so it is
        // drawn in the background colour instead.
        drawCircle(
            color = Color.Black,
            radius = w * 0.115f,
            center = Offset(w * 0.72f, h * 0.36f),
        )

        val leaf = Path().apply {
            moveTo(w * 0.52f, h * 0.24f)
            cubicTo(w * 0.56f, h * 0.10f, w * 0.68f, h * 0.04f, w * 0.74f, h * 0.03f)
            cubicTo(w * 0.75f, h * 0.14f, w * 0.66f, h * 0.24f, w * 0.52f, h * 0.24f)
            close()
        }
        drawPath(leaf, color)
    }
}

/**
 * What the blackout sequence should play.
 *
 * Three states, matching the two switches in Settings:
 *
 *  - [Themed]  bootlogo on, custom logo off: the theme ring blinks twice, then one slow Apple.
 *  - [Custom]  bootlogo on, custom logo on and an image picked: the picked image blinks twice, then
 *              one slow Apple.
 *  - [Lying]   bootlogo off: a single Apple lying on its side, shown once, with no blinking at all.
 *
 * The last one is not an animation on purpose. With the boot logo disabled iOS leaves the boot
 * screen entirely alone, and the nearest visible equivalent is one static image rather than a
 * faster blink.
 */
sealed interface BootSequence {
    data class Themed(val theme: DopamineThemeKey) : BootSequence
    data class Custom(val imagePath: String?) : BootSequence
    data object Lying : BootSequence
}

/**
 * The sequence played when a run finishes.
 *
 * Unlike iOS, it then hands control back instead of closing the app: on iOS the process is really
 * replaced and SpringBoard returns, but on Android closing the app is the whole visible result and
 * it is indistinguishable from a crash.
 */
@Composable
fun BlackoutSequence(
    sequence: BootSequence,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val markAlpha = remember { Animatable(0f) }
    val appleAlpha = remember { Animatable(0f) }

    LaunchedEffect(sequence) {
        when (sequence) {
            is BootSequence.Themed, is BootSequence.Custom -> {
                delay(450L)

                repeat(2) {
                    markAlpha.animateTo(1f, tween(220, easing = LinearEasing))
                    delay(160L)
                    markAlpha.animateTo(0f, tween(220, easing = LinearEasing))
                    delay(100L)
                }

                delay(250L)

                appleAlpha.animateTo(1f, tween(900, easing = LinearEasing))
                delay(700L)
                appleAlpha.animateTo(0f, tween(400, easing = LinearEasing))

                delay(200L)
            }

            is BootSequence.Lying -> {
                // Shown once and held, then faded out so the return to the main screen is not a cut.
                delay(600L)
                appleAlpha.animateTo(1f, tween(400, easing = LinearEasing))
                delay(1400L)
                appleAlpha.animateTo(0f, tween(500, easing = LinearEasing))
                delay(150L)
            }
        }

        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (sequence) {
            is BootSequence.Themed -> {
                /*
                 * The theme's own artwork, not the app icon.
                 *
                 * What blinks on iOS is the boot logo built by `-[DOTheme generateBootLogo]`: the
                 * theme background with `DopamineLogo` - the notched ring - drawn centred on top.
                 * An app icon stood there until now, which is a different picture; the ring is the
                 * one the original blinks, and it is drawn over the theme colour here.
                 */
                BootRing(
                    theme = sequence.theme,
                    alpha = markAlpha.value,
                )
                AppleGlyph(
                    modifier = Modifier.graphicsLayer { alpha = appleAlpha.value },
                    size = 104.dp,
                )
            }

            is BootSequence.Custom -> {
                CustomBootImage(
                    path = sequence.imagePath,
                    alpha = markAlpha.value,
                )
                AppleGlyph(
                    modifier = Modifier.graphicsLayer { alpha = appleAlpha.value },
                    size = 104.dp,
                )
            }

            is BootSequence.Lying -> {
                AppleGlyph(
                    modifier = Modifier.graphicsLayer { alpha = appleAlpha.value },
                    size = 104.dp,
                    lying = true,
                )
            }
        }
    }
}

/**
 * The ring from `DopamineLogo.pdf` on the theme's colour.
 *
 * The background is the theme's `actionMenuColor` rather than the photograph: the plist draws the
 * boot logo from the theme's photo, but at boot-screen size the photo reads as noise, and the
 * request was for the theme colour with the ring on it. The colour is still the theme's own value,
 * so each theme blinks its own colour.
 */
@Composable
private fun BootRing(
    theme: DopamineThemeKey,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = theme.actionMenuColor.copy(alpha = 1f))
        }
        /*
         * The ring is `DopamineLogo.pdf` rasterised once: the PDF is a vector, and painterResource
         * only takes a vector *Android* drawable, not a PDF. The PNG is transparent inside and
         * outside the band, so the theme colour shows through both.
         */
        Image(
            painter = painterResource(R.drawable.dopamine_ring),
            contentDescription = "Dopamine",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * The picture picked in Settings, read from the app's private directory.
 *
 * It is copied in at pick time rather than referenced by URI: a picker grant can be revoked once
 * the process dies, and the boot sequence must not depend on something it cannot re-open.
 */
@Composable
private fun CustomBootImage(
    path: String?,
    alpha: Float,
) {
    val bitmap = remember(path) {
        path?.let { p ->
            runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { this.alpha = alpha },
        )
    } else {
        // No picture, or it could not be decoded: fall back to the ring rather than a blank frame.
        BootRing(theme = LocalDopamineTheme.current, alpha = alpha)
    }
}

/**
 * "Restart userspace": the same blink, without closing the app.
 *
 * Kept separate from [BlackoutSequence] so a userspace restart cannot accidentally finish the
 * activity — that would look like a crash rather than a restart.
 */
@Composable
fun UserspaceBlink(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    // Not named `alpha`: inside graphicsLayer the scope's own `alpha` would shadow it.
    val markAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(300L)
        repeat(2) {
            markAlpha.animateTo(1f, tween(200, easing = LinearEasing))
            delay(140L)
            markAlpha.animateTo(0f, tween(200, easing = LinearEasing))
            delay(90L)
        }
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        /*
         * The wordmark, not the ring, and not the Apple glyph.
         *
         * This is the screen shown while the userspace restarts, and what iOS puts there is the
         * Dopamine logo - the same artwork as the header. Drawing the ring here was the "一片白"
         * the screen kept showing: a white shape on black with no word in it. The Apple glyph
         * belongs to the blackout sequence instead, because that one ends with the app closing.
         */
        Image(
            painter = painterResource(R.drawable.dopamine_wordmark),
            contentDescription = "Dopamine",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(220.dp)
                .graphicsLayer { alpha = markAlpha.value },
        )
    }
}

/**
 * The thin indeterminate arc drawn inside the jailbreak button while a run is going, matching
 * DODoubleHelixIndicator in the iOS source.
 */
@Composable
fun ProgressRing(
    modifier: Modifier = Modifier,
    color: Color = DopamineColors.TextPrimary,
    size: Dp = 20.dp,
) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            turn.animateTo(360f, tween(1100, easing = LinearEasing))
            turn.snapTo(0f)
        }
    }
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.14f
        drawArc(
            color = color,
            startAngle = turn.value,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = stroke),
        )
    }
}