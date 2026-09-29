package app.dizzify.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.dizzify.R
import app.dizzify.ui.theme.*

@Composable
fun LauncherSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search apps...",
    focusRequester: FocusRequester = remember { FocusRequester() },
    onVoiceSearch: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) 
            LauncherColors.DarkSurfaceVariant 
        else 
            LauncherColors.DarkSurface.copy(alpha = 0.7f),
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "search_bg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) 
            LauncherColors.AccentBlue 
        else 
            Color.Transparent,
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "search_border"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "search_scale"
    )
    
    Row(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = LauncherSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.search_desc),
            modifier = Modifier.size(24.dp),
            tint = if (isFocused) LauncherColors.AccentBlue else LauncherColors.TextSecondary
        )
        
        Spacer(modifier = Modifier.width(LauncherSpacing.md))
        
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onSearch()
                            true
                        } else false
                    },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                singleLine = true,
                cursorBrush = SolidColor(LauncherColors.AccentBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LauncherColors.TextTertiary
                )
            }
        }
        
        // Clear button
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.clear_desc),
                    tint = LauncherColors.TextSecondary
                )
            }
        }
        
        // Voice search button
        if (onVoiceSearch != null) {
            Spacer(modifier = Modifier.width(LauncherSpacing.xs))
            
            IconButton(
                onClick = onVoiceSearch,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.voice_search_desc),
                    tint = LauncherColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun graphicsLayer(block: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit): Modifier {
    return Modifier.graphicsLayer(block)
}