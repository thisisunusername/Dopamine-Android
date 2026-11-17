package com.opa334.dopamine.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The header, matching DOHeaderView.
 *
 * iOS builds a vertical UIStackView with 2 pt spacing: the logo at a fixed 40 pt height, then
 * the dark-mode version string in white 14 pt medium, then the credits line in white 60% 14 pt
 * regular. The logo's image is `DopamineLogo.pdf`, which builds a solid disc, so the mark here
 * is a filled circle rather than a wordmark.
 */
@Composable
fun DopamineHeader(
    subtitle: String,
    credits: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        DopamineMark(size = 40.dp)

        Spacer(Modifier.height(2.dp))

        Text(
            text = subtitle,
            color = DopamineColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = credits,
            color = DopamineColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/**
 * The Dopamine mark: a solid disc.
 *
 * The iOS asset (DopamineLogo.pdf) renders as a filled circle, so this is drawn rather than
 * shipped as a bitmap — it stays sharp at every size the app uses it at, and the blur/blackout
 * sequences need it at several different sizes.
 */
@Composable
fun DopamineMark(
    modifier: Modifier = Modifier,
    color: Color = DopamineColors.TextPrimary,
    size: Dp = 40.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        drawCircle(color = color, radius = this.size.minDimension / 2f)
    }
}

/**
 * The Apple silhouette shown once, slowly, after the logo blinks.
 *
 * Drawn as a path rather than shipped as an asset: the real logo is a trademark that does not
 * belong in this repository. This is a rounded fruit shape with a leaf, which is what the
 * animation needs in order to be legible.
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
 * The black-out sequence played when a run finishes.
 *
 * Order is fixed by what the user sees on iOS: screen goes black, the mark blinks twice, then a
 * single slow Apple glyph, then the app closes itself. Each phase has its own duration so the
 * Apple step can be noticeably slower, which is what makes the sequence feel deliberate rather
 * than a generic fade.
 */
@Composable
fun BlackoutSequence(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val markAlpha = remember { Animatable(0f) }
    val appleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
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
        DopamineMark(
            modifier = Modifier.graphicsLayer { alpha = markAlpha.value },
            size = 120.dp,
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