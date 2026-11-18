package com.opa334.dopamine.ui

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
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
 * The theme's `titleShadow` flag adds a shadow behind both text lines. Only ElleKit sets it among
 * the themes this build ships, and it is not decoration: that background is light enough that
 * white text without a shadow stops being legible over the photo.
 */
@Composable
fun DopamineHeader(
    subtitle: String,
    credits: String,
    modifier: Modifier = Modifier,
) {
    val palette = dopaminePalette
    val shadow = if (palette.titleShadow) {
        Shadow(color = Color(0x99000000), offset = Offset(0f, 1f), blurRadius = 3f)
    } else {
        null
    }
    val textStyle = if (shadow != null) TextStyle(shadow = shadow) else TextStyle.Default

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
            style = textStyle,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = credits,
            color = DopamineColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            style = textStyle,
        )
    }
}

/**
 * The Apple silhouette used by the boot sequence.
 *
 * Drawn as a path rather than shipped as an asset: the real logo is a trademark that does not
 * belong in this repository.
 *
 * There is no rotation option. An earlier revision had one, for a "lying Apple" shown when the
 * boot logo was switched off; that state does not exist in the original (the switch deletes the
 * boot logo file and the system draws its own), so the only thing the option could do was draw a
 * wrong-looking Apple. The glyph is always upright.
 */
@Composable
fun AppleGlyph(
    modifier: Modifier = Modifier,
    color: Color = DopamineColors.TextPrimary,
    size: Dp = 96.dp,
) {
    Canvas(modifier = modifier.size(size)) {
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
 * What a blackout should play.
 *
 * Three states, matching the two switches in Settings:
 *
 *  - [Themed]  bootlogo on, custom logo off: the theme ring blinks twice, then one slow Apple.
 *  - [Custom]  bootlogo on, custom logo on and an image picked: the picked image blinks twice, then
 *              one slow Apple.
 *  - [Off]     bootlogo off: nothing at all, which is what the switch means.
 */
sealed interface BootSequence {
    data class Themed(val theme: DopamineThemeKey) : BootSequence
    data class Custom(val imagePath: String?) : BootSequence

    /**
     * Boot logo disabled: no artwork, just the black screen.
     *
     * This used to draw an Apple rotated 90 degrees. That was invented, not copied: on iOS the
     * switch means "stop replacing the boot logo", and `-[DOEnvironmentManager updateBootLogo]`
     * answers it by deleting `/basebin/bootlogo.jp2` - the device then shows its own boot logo,
     * which the app has nothing to do with. Nothing anywhere in the iOS sources draws a sideways
     * Apple, and the only rotation in the boot path is `draw_image_to_buf`, which rotates to the
     * display's native orientation (0 on a phone, 90 on a landscape-base iPad) so the image is
     * upright for the viewer - never a decorative tilt.
     *
     * Drawing a rotated Apple was therefore both an invention and the bug: the glyph's own
     * orientation is fixed, so rotating it can only make it wrong. With the switch off the
     * sequence now shows only the black screen, which is the truthful rendering of "no boot logo".
     */
    data object Off : BootSequence
}

/**
 * The sequence played when a run finishes, and when the userspace is restarted.
 *
 * Both entry points use this one composable and pass the sequence they want, so the switches in
 * Settings apply to both. "Restart userspace" used to have its own private blink that always showed
 * the wordmark, which is why a custom boot logo had no effect there: that path never looked at the
 * setting at all.
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
                /*
                 * Timings roughly doubled.
                 *
                 * At the previous speeds the whole thing was over before the eye caught it: two
                 * 220 ms fades with 160 ms holds read as a single flicker, and the Apple then
                 * appeared and left so quickly it looked like a glitch rather than a boot screen.
                 * The blink is the only feedback that the jailbreak took effect, so it is worth
                 * spending a couple of seconds on.
                 */
                delay(600L)

                repeat(2) {
                    markAlpha.animateTo(1f, tween(420, easing = LinearEasing))
                    delay(320L)
                    markAlpha.animateTo(0f, tween(420, easing = LinearEasing))
                    delay(260L)
                }

                delay(400L)

                appleAlpha.animateTo(1f, tween(1_300, easing = LinearEasing))
                delay(1_400L)
                appleAlpha.animateTo(0f, tween(700, easing = LinearEasing))

                delay(300L)
            }

            is BootSequence.Off -> {
                // Nothing to show: "boot logo disabled" means the black screen simply stays.
                // Still held so the transition is not instantaneous - it is a pause, not a flash.
                delay(2_400L)
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
                 * The theme's own ring, not the app icon.
                 *
                 * What blinks on iOS is the boot logo built by `-[DOTheme generateBootLogo]`: the
                 * theme background with `DopamineLogo` - the notched ring - drawn centred on top.
                 */
                BootRing(theme = sequence.theme, alpha = markAlpha.value)
                AppleGlyph(
                    modifier = Modifier.graphicsLayer { alpha = appleAlpha.value },
                    size = 104.dp,
                )
            }

            is BootSequence.Custom -> {
                CustomBootImage(path = sequence.imagePath, alpha = markAlpha.value)
                AppleGlyph(
                    modifier = Modifier.graphicsLayer { alpha = appleAlpha.value },
                    size = 104.dp,
                )
            }

            is BootSequence.Off -> {
                // No artwork at all, so there is nothing to place in the centre.
            }
        }
    }
}

/**
 * The ring from `DopamineLogo.pdf` on the theme's colour.
 *
 * The background is the theme's `actionMenuColor` rather than the photograph: at boot-screen size
 * the photo reads as noise, and what was asked for is the theme colour with the ring on it. The
 * colour is still the theme's own value, so each theme blinks its own colour.
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