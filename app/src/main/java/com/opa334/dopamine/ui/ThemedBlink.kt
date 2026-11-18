package com.opa334.dopamine.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * The two halves of the boot animation, for tearing them apart.
 *
 * `BlackoutSequence` runs as one unit: it starts when it is placed in the tree, plays the mark
 * and then the Apple, and calls back when done. That is the right shape for a fresh run, where
 * the animation is what covers the work that just finished.
 *
 * It is the wrong shape for "Restart userspace", where the restart and the blink run at the same
 * time and the *Apple* is the part that has to land after the userspace is back - the Apple is
 * what the system shows while it comes up. With the sequence as a single block, the Apple played
 * on whatever schedule the animation had, regardless of whether the restart had actually reached
 * that point.
 *
 * These two composables are the same artwork, split: [ThemedMarkBlink] is the theme colour and the
 * ring, [AppleStage] is the Apple on black. Both are drawn as full-screen opaque layers so that
 * neither can show the main screen's blurred photo through it.
 *
 * [AppleStage] is kept as its own composable rather than folded into the sequence because it is
 * the piece that has to be held on screen until the restart finishes: the caller keeps it
 * composed for as long as it needs, and removes it when it is done.
 */

/**
 * The part of the boot logo that blinks: the theme's colour, full screen, with the ring centred.
 *
 * Black underneath, so that the fade starts from black rather than from the main screen.
 */
@Composable
fun ThemedMarkBlink(
    sequence: BootSequence,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val markAlpha = remember { Animatable(0f) }

    LaunchedEffect(sequence) {
        delay(800L)
        repeat(2) {
            markAlpha.animateTo(1f, tween(550, easing = LinearEasing))
            delay(1_000L)
            markAlpha.animateTo(0f, tween(550, easing = LinearEasing))
            delay(350L)
        }
        markAlpha.snapTo(0f)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        /*
         * Always drawn while the mark is playing, at whatever alpha it currently has.
         *
         * This used to be wrapped in `if (markAlpha.value > 0f)`, to keep the mark out of the
         * Apple's frames. The cost was worse than the benefit: the whole blink - colour and ring
         * together - was dropped on the very first frame, and any frame where the value had not
         * moved yet, so a theme could blink and show nothing at all. The mark is drawn
         * unconditionally now and its own alpha does the fading; at 0 it is invisible anyway, and
         * the Apple stage draws its own black fill on top, so nothing leaks between them.
         */
        when (sequence) {
            is BootSequence.Themed -> BootRing(sequence.theme, markAlpha.value)
            is BootSequence.Custom -> CustomBootImage(sequence.imagePath, markAlpha.value)
            is BootSequence.Off -> Unit
        }
    }
}

/**
 * The Apple on a black screen, faded in and out.
 *
 * Always drawn on its own opaque black fill: the Apple is the moment the theme is gone, and it is
 * the one image that must never sit on a tinted background.
 */
@Composable
fun AppleStage(
    modifier: Modifier = Modifier,
    alpha: Float,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        AppleGlyph(
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
            size = 104.dp,
        )
    }
}
