package app.pokeplan.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation

/** A whole-number box: the number keyboard, only digits kept, at most [maxDigits] of them. */
@Composable
fun DigitsField(
    value: String,
    onChange: (String) -> Unit,
    maxDigits: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    large: Boolean = false,
    prefix: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) = PixelField(
    value, { onChange(it.filter(Char::isDigit).take(maxDigits)) }, modifier, label, placeholder, helper, error, enabled,
    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), keyboardActions = keyboardActions, large = large, prefix = prefix,
)

/** A figure with a decimal point or comma: the decimal keyboard, only digits, `.` and `,` kept, at most [maxChars]. */
@Composable
fun DecimalField(
    value: String,
    onChange: (String) -> Unit,
    maxChars: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    large: Boolean = false,
    prefix: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) = PixelField(
    value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(maxChars)) }, modifier, label, placeholder, helper, error, enabled,
    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), keyboardActions = keyboardActions, large = large, prefix = prefix,
)

@Composable
fun PixelField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    large: Boolean = false,
    prefix: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val off = Pixel.InkSoft.copy(alpha = 0.55f)
    val edge = when {
        error != null -> Pixel.Danger
        focused -> Pixel.Blue
        !enabled -> Pixel.Border.copy(alpha = 0.5f)
        else -> Pixel.Border
    }
    val body = (if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge).copy(color = if (enabled) Pixel.Ink else off, baselineShift = androidx.compose.ui.text.style.BaselineShift.None)
    val soft = MaterialTheme.typography.bodyMedium
    Column(modifier.semantics { if (error != null) error(error) }, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        if (label != null) Text(label, style = soft, color = if (error != null) Pixel.Danger else Pixel.InkSoft)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).pixelFrame(if (enabled) Pixel.Panel else Pixel.Shade, edge)
                .semantics { (label ?: placeholder)?.let { contentDescription = it } },
            enabled = enabled, readOnly = readOnly, textStyle = body, cursorBrush = SolidColor(if (error != null) Pixel.Danger else Pixel.Blue),
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, minLines = minLines, maxLines = maxLines, interactionSource = interaction,
            decorationBox = { inner ->
                Box(Modifier.defaultMinSize(minHeight = Spacing.Target).padding(horizontal = Spacing.M, vertical = Spacing.S), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.offset(y = (-2.25).dp)) {
                        if (prefix != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            Text(prefix, style = body.copy(color = Pixel.InkSoft))
                            Box { if (value.isEmpty() && placeholder != null) Text(placeholder, style = body.copy(color = Pixel.InkSoft)); inner() }
                        } else {
                            if (value.isEmpty() && placeholder != null) Text(placeholder, style = body.copy(color = Pixel.InkSoft))
                            inner()
                        }
                    }
                }
            },
        )
        (error ?: helper)?.let { Text(it, style = soft, color = if (error != null) Pixel.Danger else Pixel.InkSoft) }
    }
}
