package com.opa334.dopamine.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The wordmark, drawn rather than shipped as an image.
 *
 * A vector keeps the APK small and, more importantly, stays sharp at the 44 sp used on the main
 * screen. The letterforms are a plain geometric sans to match the heavy weight Dopamine uses.
 */
@Composable
fun DopamineWordmark(modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = "Dopamine",
        style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
        color = DopamineColors.TextPrimary,
        modifier = modifier,
    )
}

/**
 * The swirl mark shown while the "logo blink" plays.
 *
 * Two interleaved arcs, which is enough to read as the app's icon at this size and avoids bundling a
 * bitmap for a shape that is only on screen for a second.
 */
@Composable
fun DopamineMark(
    modifier: Modifier = Modifier,
    color: Color = DopamineColors.TextPrimary,
    size: androidx.compose.ui.unit.Dp = 120.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.09f
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = color,
            startAngle = 40f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = 220f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
    }
}

/**
 * The Apple silhouette shown once, slowly, after the logo blinks.
 *
 * Drawn as a path because the real logo is a trademark that should not be shipped as an asset; this
 * is a rounded fruit shape with a leaf, which is what the animation needs to be legible.
 */
@Composable
fun AppleGlyph(
    modifier: Modifier = Modifier,
    color: Color = DopamineColors.TextPrimary,
    size: androidx.compose.ui.unit.Dp = 96.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            // Body: a wide rounded blob narrower at the top, which is the silhouette's main read.
            moveTo(w * 0.50f, h * 0.28f)
            cubicTo(w * 0.30f, h * 0.28f, w * 0.14f, h * 0.44f, w * 0.14f, h * 0.64f)
            cubicTo(w * 0.14f, h * 0.86f, w * 0.32f, h * 0.98f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.68f, h * 0.98f, w * 0.86f, h * 0.86f, w * 0.86f, h * 0.64f)
            cubicTo(w * 0.86f, h * 0.44f, w * 0.70f, h * 0.28f, w * 0.50f, h * 0.28f)
            close()
        }
        drawPath(path, color)

        // Bite, on the right: a notch is what makes the shape unmistakable at small sizes.
        drawCircle(
            color = DopamineColors.Background,
            radius = w * 0.115f,
            center = Offset(w * 0.72f, h * 0.36f),
        )

        // Leaf, tilted to the right off the top centre.
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
 * The black-out sequence played when a run finishes.
 *
 * Order is fixed by what the user sees on iOS: screen goes black, the Dopamine mark blinks twice,
 * then a single slow Apple glyph, then the app closes itself. Each phase has its own duration so the
 * Apple step can be noticeably slower, which is the detail that makes the sequence feel deliberate
 * rather than a generic fade.
 */
@Composable
fun BlackoutSequence(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    // One animatable per element: the mark fades twice independently of the Apple glyph.
    val markAlpha = remember { Animatable(0f) }
    val appleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Hold pure black so the transition reads as the screen switching off.
        delay(450L)

        // 2. Dopamine mark, twice. Each blink is a fade in and out; 700 ms per cycle is slow enough
        //    to be legible and quick enough that the whole sequence stays under four seconds.
        repeat(2) {
            markAlpha.animateTo(1f, tween(220, easing = LinearEasing))
            delay(160L)
            markAlpha.animateTo(0f, tween(220, easing = LinearEasing))
            delay(100L)
        }

        // 3. A gap, so the Apple glyph is clearly a separate beat rather than a morph.
        delay(250L)

        // 4. The Apple glyph appears slowly — 900 ms in, 700 ms held, 400 ms out.
        appleAlpha.animateTo(1f, tween(900, easing = LinearEasing))
        delay(700L)
        appleAlpha.animateTo(0f, tween(400, easing = LinearEasing))

        // 5. Black again, then the caller finishes the activity.
        delay(200L)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        DopamineMark(
            modifier = Modifier.graphicsLayer { alpha = markAlpha.value },
            size = 128.dp,
        )
        AppleGlyph(
            modifier = Modifier.graphicsLayer { alpha = appleAlpha.value },
            size = 104.dp,
        )
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
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(300L)
        repeat(2) {
            alpha.animateTo(1f, tween(200, easing = LinearEasing))
            delay(140L)
            alpha.animateTo(0f, tween(200, easing = LinearEasing))
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
        DopamineMark(
            modifier = Modifier.graphicsLayer { alpha = alpha.value },
            size = 120.dp,
        )
    }
}