package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.KeyMoment
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_action_complete_set
import com.mooscles.designsystem.resources.ds_action_uncomplete_set
import com.mooscles.designsystem.resources.ds_complete_set_n
import com.mooscles.designsystem.resources.ds_set_n_completed
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The scale and opacity the check disc is drawn with; exposed so tests can read the entrance frame by frame. */
internal val SetCheckScale = SemanticsPropertyKey<Float>("SetCheckScale")
internal val SetCheckAlpha = SemanticsPropertyKey<Float>("SetCheckAlpha")
private var SemanticsPropertyReceiver.setCheckScale by SetCheckScale
private var SemanticsPropertyReceiver.setCheckAlpha by SetCheckAlpha

/**
 * The "set done" check: a 44 dp disc in a 56 dp target, a checkbox labelled "Complete set N",
 * and, once checked, "Set N completed", with its double tap read as "mark set as not done": a
 * screen reader never offers "Complete set N" on a set that is done, where the tap undoes it.
 * Checked it is filled with a check mark (an empty ring otherwise), so the state is not by color
 * alone. Checking plays the `SetCompleted` key moment (a spring, or a fade with reduced motion).
 * With [haptics] it gives `SetCompleted`/`SetUnchecked`; [SetRow] turns it off and fires them from
 * the row status, so a personal record can replace the plain one.
 */
@Composable
fun SetCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    setNumber: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptics: Boolean = true,
) {
    val colors = MTheme.colors
    val feedback = MTheme.haptics
    val source = remember { MutableInteractionSource() }
    val label = stringResource(if (checked) Res.string.ds_set_n_completed else Res.string.ds_complete_set_n, setNumber)
    val action = stringResource(if (checked) Res.string.ds_action_uncomplete_set else Res.string.ds_action_complete_set)
    val reduced = MTheme.reducedMotion
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val choice = MotionPolicy.keyMoment(KeyMoment.SetCompleted, reduced)
    var previous by remember { mutableStateOf(checked) }
    // From the composition that first sees the check until the effect below has snapped to the
    // start, the disc is drawn at the start of the entrance: no frame shows the finished disc first.
    val entering = checked && !previous
    LaunchedEffect(checked) {
        if (!checked || previous) {
            previous = checked
            return@LaunchedEffect
        }
        try {
            when (choice) {
                is MotionChoice.Spring -> {
                    scale.snapTo(choice.fromScale)
                    previous = true
                    scale.animateTo(1f, spring(choice.tokens.dampingRatio, choice.tokens.stiffness))
                }
                is MotionChoice.Fade -> {
                    alpha.snapTo(0f)
                    previous = true
                    alpha.animateTo(1f, tween(choice.durationMs))
                }
            }
        } finally {
            // Unchecking in the middle of the entrance must not leave the disc small or faint.
            withContext(NonCancellable) {
                previous = checked
                scale.snapTo(1f)
                alpha.snapTo(1f)
            }
        }
    }
    val drawnScale = { if (entering && choice is MotionChoice.Spring) choice.fromScale else scale.value }
    val drawnAlpha = { if (entering && choice is MotionChoice.Fade) 0f else alpha.value }
    val fill = when {
        !checked -> colors.bgSurface
        enabled -> colors.accentSolid
        else -> colors.textDisabled
    }
    val outline = when {
        checked -> fill
        enabled -> colors.borderStrong
        else -> colors.borderSubtle
    }
    Box(
        modifier
            .sizeIn(minWidth = MTheme.targets.workout, minHeight = MTheme.targets.workout)
            .mPressScale(source, enabled)
            .semantics {
                contentDescription = label
                toggleableState = ToggleableState(checked)
                setCheckScale = drawnScale()
                setCheckAlpha = drawnAlpha()
            }
            // A toggle drawn as a click with its own action label: `toggleable` cannot name the
            // action, and the name of the double tap is what tells "complete" from "undo".
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClickLabel = action,
                role = Role.Checkbox,
            ) {
                val next = !checked
                if (haptics) feedback.perform(if (next) HapticEvent.SetCompleted else HapticEvent.SetUnchecked)
                onCheckedChange(next)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(MTheme.sizes.checkDisc)
                .mFocusRing(CircleShape, source)
                .graphicsLayer {
                    scaleX = drawnScale()
                    scaleY = drawnScale()
                    this.alpha = drawnAlpha()
                }
                .background(fill, CircleShape)
                .border(MTheme.sizes.border, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) MIcon(MIcons.Check, contentDescription = null, size = MTheme.sizes.icon24, tint = colors.accentOn)
        }
    }
}
