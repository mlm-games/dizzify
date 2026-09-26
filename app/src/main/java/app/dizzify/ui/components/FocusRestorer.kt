package app.dizzify.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * Holds one [FocusRequester] per item key and remembers which key was last focused.
 *
 * Requesters are keyed by stable item identity because a `FocusRequester` is bound to the node
 * it is attached to; keying by list index would rebind it to a different item on every
 * reorder or removal and make `restoreFocus()` target the wrong card.
 */
class FocusRestorer {
    private var lastFocusedKey: String? = null
    private val focusRequesters = mutableMapOf<String, FocusRequester>()

    fun getFocusRequester(key: String): FocusRequester =
        focusRequesters.getOrPut(key) { FocusRequester() }

    fun saveFocus(key: String) {
        lastFocusedKey = key
    }

    fun restoreFocus() {
        lastFocusedKey?.let { focusRequesters[it] }?.let {
            runCatching { it.requestFocus() }
        }
    }
}

@Composable
fun rememberFocusRestorer(): FocusRestorer = remember { FocusRestorer() }
