package com.opa334.dopamine.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opa334.dopamine.R
import com.opa334.dopamine.core.StepEvent

/** One row of the log under the progress bar. */
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

/**
 * The main screen.
 *
 * Layout follows Dopamine exactly: a heavy wordmark at the top, a deliberately over-precise subtitle
 * under it, a card of actions, and a single very large "Jailbreak" card pinned near the bottom. The
 * log only appears while a run is in progress, which is why it animates in rather than reserving
 * space — on iOS the header slides up and the button grows to fill the screen.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onJailbreak: () -> Unit,
    onRestartSpringBoard: () -> Unit,
    onRestartUserspace: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onRemoveJailbreak: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DopamineColors.Background)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(48.dp))

        // ---- Header ----
        DopamineWordmark()
        Spacer(Modifier.height(6.dp))
        Text(
            // Deliberately over-precise, which is the joke the original subtitle makes.
            text = stringResource(R.string.subtitle_version),
            color = DopamineColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.subtitle_credits),
            color = DopamineColors.TextSecondary,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(28.dp))

        // ---- Action card ----
        DopamineCard {
            ActionRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.menu_settings),
                enabled = true,
                onClick = onSettings,
            )
            CardDivider()
            ActionRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.menu_restart_springboard),
                enabled = state.jailbroken,
                onClick = onRestartSpringBoard,
            )
            CardDivider()
            ActionRow(
                icon = Icons.Filled.RestartAlt,
                title = stringResource(R.string.menu_restart_userspace),
                enabled = state.jailbroken,
                onClick = onRestartUserspace,
            )
            CardDivider()
            ActionRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.menu_about),
                enabled = true,
                onClick = onAbout,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- Log, only while expanded ----
        AnimatedVisibility(
            visible = state.expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                LogPanel(state = state)
                Spacer(Modifier.height(16.dp))
            }
        }

        // Pushes the jailbreak card towards the bottom without hard-coding a screen height.
        Spacer(Modifier.weight(1f))

        // ---- Jailbreak card ----
        JailbreakCard(
            // Falls back to a resource so the very first frame, before MainActivity has read the
            // daemon, still shows a label rather than an empty button.
            title = state.title.ifEmpty {
                stringResource(
                    if (state.jailbroken) R.string.action_jailbroken else R.string.action_jailbreak,
                )
            },
            busy = state.busy,
            enabled = !state.busy,
            jailbroken = state.jailbroken,
            onClick = onJailbreak,
            onRemove = onRemoveJailbreak,
        )

        Spacer(Modifier.height(28.dp))

        // Shown only when the app knows which package manager is installed, so a fresh install does
        // not display an empty "package manager: " line.
        state.packageManager?.let {
            Text(
                text = stringResource(R.string.footer_package_manager, it),
                color = DopamineColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.deviceLine.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                color = DopamineColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DopamineCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DopamineColors.Menu),
    ) {
        content()
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = DopamineColors.Separator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 52.dp),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // A disabled row uses a lower alpha rather than a different colour, which is how iOS greys out;
    // a colour swap reads as a different state instead of an unavailable one.
    val contentAlpha = if (enabled) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DopamineColors.TextPrimary.copy(alpha = contentAlpha),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = DopamineColors.TextPrimary.copy(alpha = contentAlpha),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun LogPanel(state: MainUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DopamineColors.Menu)
            .padding(16.dp),
    ) {
        val animated by animateFloatAsState(targetValue = state.progress, label = "progress")
        LinearProgressIndicator(
            progress = { animated },
            color = DopamineColors.TextPrimary,
            trackColor = DopamineColors.Separator,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )

        Spacer(Modifier.height(14.dp))

        state.rows.forEach { row ->
            LogRowView(row)
        }
    }
}

@Composable
private fun LogRowView(row: LogRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (row.state) {
                RowState.Pending -> Box(
                    Modifier
                        .size(7.dp)
                        .background(DopamineColors.TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                )
                RowState.Running -> CircularProgressIndicator(
                    strokeWidth = 1.8.dp,
                    color = DopamineColors.TextSecondary,
                    modifier = Modifier.size(13.dp),
                )
                RowState.Done -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = DopamineColors.Success,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = row.label,
                color = if (row.state == RowState.Pending) {
                    DopamineColors.TextSecondary
                } else {
                    DopamineColors.TextPrimary
                },
                fontSize = 14.sp,
                // Finished steps turn green, which is the only colour in the log.
                fontWeight = if (row.state == RowState.Done) FontWeight.Medium else FontWeight.Normal,
            )
            row.detail?.let {
                Text(
                    text = it,
                    color = DopamineColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * The big bottom card.
 *
 * It is white, not grey, because that is what makes it the single obvious action on the screen; the
 * rest of the interface is deliberately dim so this is the only thing that draws the eye.
 */
@Composable
private fun JailbreakCard(
    title: String,
    busy: Boolean,
    enabled: Boolean,
    jailbroken: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.7f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (jailbroken) DopamineColors.MenuRaised else Color.White)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = if (jailbroken) DopamineColors.TextPrimary else DopamineColors.Background,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = title,
                color = if (jailbroken) DopamineColors.TextPrimary else DopamineColors.Background,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    // "Remove jailbreak" only exists once there is something to remove; putting it under the main
    // card instead of inside it keeps the primary action a single full-width target.
    if (jailbroken) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.menu_remove_jailbreak),
            color = DopamineColors.Danger,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DopamineColors.Menu)
                .clickable(onClick = onRemove)
                .padding(vertical = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Drives the log from [StepEvent]s.
 *
 * Kept as a pure function of the event stream so the screen has no hidden state: each event either
 * appends a row, flips one to running or done, or moves the bar.
 */
fun MainUiState.applyEvent(event: StepEvent, totalSteps: Int): MainUiState {
    val denom = totalSteps.coerceAtLeast(1)
    return when (event) {
        // Header lines are informational; the rows are built from the step list up front, so there is
        // nothing to append here.
        is StepEvent.Log -> this
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