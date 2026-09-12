package app.dizzify.ui.dialogs

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dizzify.ui.theme.*

private const val MAX_PIN_LENGTH = 6

@Composable
fun PinLockDialog(
    isSettingPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    validateError: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var phase by remember { mutableIntStateOf(0) } // set mode: 0 = enter, 1 = confirm
    var localError by remember { mutableStateOf("") }

    // Failed unlock attempt from the caller: show the error and clear entry.
    LaunchedEffect(validateError) {
        if (validateError != null) {
            localError = validateError
            pin = ""
        }
    }

    val entering = if (!isSettingPin) pin else if (phase == 0) pin else confirmPin

    fun appendDigit(d: Char) {
        localError = ""
        if (isSettingPin && phase == 1) {
            if (confirmPin.length < MAX_PIN_LENGTH) confirmPin += d
        } else {
            if (pin.length < MAX_PIN_LENGTH) pin += d
        }
    }

    fun backspace() {
        localError = ""
        if (isSettingPin && phase == 1) {
            confirmPin = confirmPin.dropLast(1)
        } else {
            pin = pin.dropLast(1)
        }
    }

    fun submit() {
        if (isSettingPin) {
            if (phase == 0) {
                if (pin.isEmpty()) {
                    localError = "PIN cannot be empty"
                } else {
                    phase = 1
                    localError = ""
                }
            } else {
                if (confirmPin.isEmpty()) {
                    localError = "Please confirm the PIN"
                } else if (pin != confirmPin) {
                    localError = "PINs do not match"
                    pin = ""
                    confirmPin = ""
                    phase = 0
                } else {
                    onConfirm(pin)
                }
            }
        } else {
            if (pin.isEmpty()) {
                localError = "Please enter PIN"
            } else {
                onConfirm(pin)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                // Physical remote number keys + delete.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                            appendDigit('0' + (event.nativeKeyEvent.keyCode - AndroidKeyEvent.KEYCODE_0))
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DEL -> {
                            backspace()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(LauncherColors.DarkSurface)
                    .border(2.dp, LauncherColors.AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(LauncherSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        !isSettingPin -> "Enter PIN"
                        phase == 0 -> "Set PIN"
                        else -> "Confirm PIN"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sm))

                Text(
                    text = "●".repeat(entering.length) + "○".repeat(MAX_PIN_LENGTH - entering.length),
                    style = MaterialTheme.typography.headlineLarge,
                    color = LauncherColors.AccentBlue,
                    textAlign = TextAlign.Center,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                )

                if (localError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(LauncherSpacing.sm))
                    Text(
                        text = localError,
                        color = LauncherColors.Error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(LauncherSpacing.lg))

                val firstDigitFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstDigitFocus.requestFocus() }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("123", "456", "789").forEachIndexed { row, digits ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            digits.forEachIndexed { col, d ->
                                PinKey(
                                    label = d.toString(),
                                    onClick = { appendDigit(d) },
                                    focusRequester = if (row == 0 && col == 0) firstDigitFocus else null
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        PinKey(label = "Clear", wide = true, onClick = {
                            localError = ""
                            if (isSettingPin && phase == 1) confirmPin = "" else pin = ""
                        })
                        PinKey(label = "0", onClick = { appendDigit('0') })
                        PinKey(label = "⌫", onClick = { backspace() })
                    }
                }

                Spacer(modifier = Modifier.height(LauncherSpacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { submit() }) { Text("Confirm") }
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(if (wide) 132.dp else 60.dp, 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) LauncherColors.AccentBlue
                else LauncherColors.DarkSurfaceVariant
            )
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}
