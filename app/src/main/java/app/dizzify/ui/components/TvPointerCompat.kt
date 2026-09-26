package app.dizzify.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Tap handling for TV surfaces that are driven by `focusable()` + `onKeyEvent` instead of
 * `clickable()`. Those elements answer D-pad OK but ignore touch entirely, so any launcher
 * surface that must also work on a touch device needs this.
 *
 * Focus is moved to the pressed element so D-pad navigation continues from wherever the user
 * tapped, which is what makes touch and D-pad feel like one input model.
 */
@Composable
fun Modifier.tvPointerClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    requestFocusOnPress: Boolean = true,
): Modifier = tvPointerGestures(onClick = onClick, enabled = enabled, requestFocusOnPress = requestFocusOnPress)

/**
 * As [tvPointerClick], plus touch long-press. `onKeyEvent` only ever sees hardware keys, so a
 * surface that relies on press duration for its context menu is unusable without this.
 */
@Composable
fun Modifier.tvPointerLongClick(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    requestFocusOnPress: Boolean = true,
): Modifier = tvPointerGestures(
    onClick = onClick,
    onLongClick = onLongClick,
    enabled = enabled,
    requestFocusOnPress = requestFocusOnPress
)

@Composable
private fun Modifier.tvPointerGestures(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    requestFocusOnPress: Boolean = true,
): Modifier {
    val focusRequester = remember { FocusRequester() }

    return this
        .then(
            if (requestFocusOnPress) {
                Modifier.focusRequester(focusRequester)
            } else {
                Modifier
            }
        )
        .pointerInput(enabled, onClick, onLongClick, requestFocusOnPress) {
            if (!enabled) return@pointerInput

            detectTapGestures(
                onPress = {
                    if (requestFocusOnPress) {
                        runCatching { focusRequester.requestFocus() }
                    }
                    tryAwaitRelease()
                },
                onTap = { onClick() },
                onLongPress = { onLongClick?.invoke() }
            )
        }
}
