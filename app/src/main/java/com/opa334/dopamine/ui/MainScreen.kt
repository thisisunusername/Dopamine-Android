package com.opa334.dopamine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opa334.dopamine.R
import com.opa334.dopamine.core.StepEvent

/** One line of the log shown inside the expanded button. */
data class LogRow(
    val label: String,
    val detail: String? = null,
    val state: RowState = RowState.Pending,
)

enum class RowState { Pending, Running, Done }

/** Everything the main screen needs to draw itself. */
data class MainUiState(
    val jailbroken: Boolean = false,
    val busy: Boolean = false,
    /** The button label. Empty means "not decided yet"; the screen then falls back to a resource. */
    val title: String = "",
    val progress: Float = 0f,
    val rows: List<LogRow> = emptyList(),
    val expanded: Boolean = false,
    val deviceLine: String = "",
    val packageManager: String? = null,
)

/** Sizes lifted from DOGlobalAppearance.h in the iOS sources. */
private val OuterPadding = 30.dp
private val ActionHeight = 73.dp
private val InnerPadding = 20.dp
private val InnerTopPadding = 5.dp
private val CardCorner = 14.dp

/**
 * The main screen, rebuilt to match DOMainViewController.
 *
 * iOS uses a vertical stack pinned 30 pt from the sides, sized to 73% of the window height and
 * centred, with the header inset 5 pt further and a 60 pt placeholder holding the jailbreak
 * button near the bottom. The same numbers are used here so the proportions match rather than
 * merely resemble.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onJailbreak: () -> Unit,
    onRestartSpringBoard: () -> Unit,
    onRestartUserspace: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ThemeBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                /*
                 * No verticalScroll here, and weight(1f) below instead of fillMaxSize on the
                 * spacer.
                 *
                 * The previous version combined fillMaxSize() with verticalScroll() and a
                 * weighted spacer in the same Column: an infinite height constraint cannot be
                 * distributed through weight(), so Compose threw while measuring and the app
                 * died on the first tap. The screen is now a fixed-height column that fills the
                 * window, with the action card pinned to the top and the button pinned to the
                 * bottom; nothing on it needs to scroll.
                 */
                .padding(horizontal = OuterPadding),
        ) {
            Spacer(Modifier.height(28.dp))

            // ---- Header ----
            Column(Modifier.padding(start = 5.dp)) {
                DopamineHeader(
                    subtitle = stringResource(R.string.subtitle_version),
                    credits = stringResource(R.string.subtitle_credits),
                )
            }

            Spacer(Modifier.height(26.dp))

            // ---- Action card ----
            ActionCard {
                ActionRow(Icons.Filled.Settings, stringResource(R.string.menu_settings), true, onSettings)
                Separator()
                ActionRow(
                    Icons.Filled.Refresh,
                    stringResource(R.string.menu_restart_springboard),
                    state.jailbroken,
                    onRestartSpringBoard,
                )
                Separator()
                ActionRow(
                    Icons.Filled.RestartAlt,
                    stringResource(R.string.menu_restart_userspace),
                    state.jailbroken,
                    onRestartUserspace,
                )
                Separator()
                ActionRow(Icons.Filled.Info, stringResource(R.string.menu_about), true, onAbout)
            }

            Spacer(Modifier.weight(1f))

            // ---- Log, only while a run is in progress ----
            // AnimatedVisibility is deliberately not used: the panel is only on screen during a
            // run, and the button below it must not move while rows arrive.
            if (state.expanded) {
                LogPanel(state)
                Spacer(Modifier.height(14.dp))
            }

            // ---- Jailbreak button ----
            JailbreakButton(
                title = state.title.ifEmpty {
                    stringResource(
                        if (state.jailbroken) R.string.action_jailbroken else R.string.action_jailbreak,
                    )
                },
                jailbroken = state.jailbroken,
                busy = state.busy,
                // Once the device is jailbroken the row has nothing left to do, so it goes grey
                // and stops accepting taps rather than offering a second run that would fail.
                enabled = !state.busy && !state.jailbroken,
                onClick = onJailbreak,
            )

            // The iOS layout reserves 60 pt for the button; the remaining space keeps it clear of
            // the bottom edge on tall screens.
            Spacer(Modifier.height(60.dp))
        }
    }
}

/**
 * The translucent rounded card that the action rows sit in.
 *
 * `actionMenuColor` at full strength (which is itself 45% alpha), corner radius 14 and the
 * continuous curve that iOS asks for — Compose's RoundedCornerShape is already continuous.
 */
@Composable
private fun ActionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(DopamineColors.Menu)
            .padding(horizontal = InnerPadding, vertical = InnerTopPadding),
    ) {
        content()
    }
}

/**
 * One row.
 *
 * The iOS row is a plain UIButtonConfiguration with a 14 pt medium-weight icon, 10 pt image
 * padding and white text. Disabled rows keep the text at 40% so the eye can tell them apart
 * without them vanishing into the card.
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ActionHeight)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) DopamineColors.TextPrimary else DopamineColors.TextDisabled,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = if (enabled) DopamineColors.TextPrimary else DopamineColors.TextDisabled,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun Separator() {
    HorizontalDivider(
        color = DopamineColors.Separator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

/**
 * The big button at the bottom.
 *
 * It is not white: DOJailbreakButton sets its background to `menuColorWithAlpha:1.0`, i.e. the
 * same translucent grey as the card, radius 14. The title carries a `lock.open` glyph while the
 * device is unsupported/jailbreakable and the label switches with state.
 */
@Composable
private fun JailbreakButton(
    title: String,
    jailbroken: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(CardCorner))
            .alpha(if (enabled) 1f else 0.6f)
            .background(DopamineColors.Menu)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            ProgressRing(size = 18.dp)
            Spacer(Modifier.width(10.dp))
        } else {
            Icon(
                imageVector = if (jailbroken) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = if (enabled) DopamineColors.TextPrimary else DopamineColors.TextDisabled,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            color = if (enabled) DopamineColors.TextPrimary else DopamineColors.TextDisabled,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(max = 260.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The log shown while a run is in progress.
 *
 * This is where "Detailed log" earns its name. With the switch off the panel shows only the
 * step labels and a one-word result; with it on, JailbreakEngine returns a much longer result
 * string for each step (kernel release, API level, ABI, the staging path, the resolved shell,
 * the daemon pid) and those are printed here in a monospace block.
 */
@Composable
private fun LogPanel(state: MainUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(Color(0x8C000000))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Device line first: it is the same information the header would carry on iOS.
        if (state.deviceLine.isNotEmpty()) {
            Text(
                text = state.deviceLine,
                color = DopamineColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
        }

        state.rows.forEach { row ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = when (row.state) {
                        RowState.Done -> "✓"
                        RowState.Running -> "›"
                        RowState.Pending -> "·"
                    },
                    color = when (row.state) {
                        RowState.Done -> DopamineColors.Success
                        RowState.Running -> DopamineColors.TextPrimary
                        RowState.Pending -> DopamineColors.TextSecondary
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.label,
                        color = if (row.state == RowState.Pending) {
                            DopamineColors.TextSecondary
                        } else {
                            DopamineColors.TextPrimary
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    // The detail line is the whole point of the toggle: only present when the
                    // engine was asked for detail.
                    row.detail?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = "  $it",
                            color = DopamineColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Progress bar, mirroring the thin bar under the log on iOS.
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DopamineColors.TextPrimary),
            )
        }
    }
}

/** Drives the log from [StepEvent]s so the screen holds no hidden state. */
fun MainUiState.applyEvent(event: StepEvent, totalSteps: Int): MainUiState {
    val denom = totalSteps.coerceAtLeast(1)
    return when (event) {
        is StepEvent.Log -> copy(rows = rows + LogRow(event.text))
        is StepEvent.Started -> copy(
            rows = rows.mapIndexed { i, row ->
                if (i == event.index) row.copy(state = RowState.Running) else row
            }.ifEmpty {
                listOf(LogRow(event.label, state = RowState.Running))
            },
            progress = event.index.toFloat() / denom,
        )
        is StepEvent.Finished -> copy(
            rows = rows.mapIndexed { i, row ->
                if (i == event.index) row.copy(detail = event.result, state = RowState.Done) else row
            },
            progress = (event.index + 1).toFloat() / denom,
        )
        is StepEvent.Progress -> copy(progress = event.fraction)
        is StepEvent.Done -> copy(busy = false, progress = 1f, title = event.message)
    }
}

/** Builds the initial row skeleton so the log has all its lines before the run starts. */
fun initialRows(labels: List<String>): List<LogRow> = labels.map { LogRow(it) }