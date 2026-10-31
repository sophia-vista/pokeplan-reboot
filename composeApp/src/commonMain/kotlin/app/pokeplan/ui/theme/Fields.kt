package app.pokeplan.ui.theme

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation

/*
 * The text field of the control system (the sibling of [PixelKey]; states in docs/ui-ux-brief.md, section 5.1):
 *
 *   state     container  outline               text                  notes
 *   rest      panel      border                ink                   placeholder and label in ink-soft
 *   focused   panel      2 dp blue             ink                   the same blue as a key's focus ring
 *   error     panel      2 dp danger           ink                   [error] replaces [helper], is announced, and says what to do
 *   disabled  shade      border at half        ink-soft at 55%       not editable, not focusable
 *   read-only panel      border                ink                   selectable, not editable
 *
 * Labels and placeholders are plain strings so every field reads the same: a label names the field (use it when the field is
 * alone on a line), a placeholder shows the example (use it beside a button, where a label would take the room). The corner
 * is the dialogue box's, the font is the app's, the text size is the body size.
 */
@Composable
fun PixelField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    /** A quiet line under the field (a rule said up front: "At least 6 characters"). */
    helper: String? = null,
    /** What went wrong and what to do; turns the field red and is read out. Never blames the user. */
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    // The colours come from [colors] (per state), so the styles carry none of their own.
    val body = MaterialTheme.typography.bodyLarge.copy(color = Color.Unspecified)
    val soft = MaterialTheme.typography.bodyMedium.copy(color = Color.Unspecified)
    val off = Pixel.InkSoft.copy(alpha = 0.55f)
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Pixel.Panel, unfocusedContainerColor = Pixel.Panel, errorContainerColor = Pixel.Panel, disabledContainerColor = Pixel.Shade,
        focusedBorderColor = Pixel.Blue, unfocusedBorderColor = Pixel.Border, errorBorderColor = Pixel.Danger, disabledBorderColor = Pixel.Border.copy(alpha = 0.5f),
        focusedTextColor = Pixel.Ink, unfocusedTextColor = Pixel.Ink, errorTextColor = Pixel.Ink, disabledTextColor = off,
        focusedLabelColor = Pixel.Blue, unfocusedLabelColor = Pixel.InkSoft, errorLabelColor = Pixel.Danger, disabledLabelColor = off,
        focusedPlaceholderColor = Pixel.InkSoft, unfocusedPlaceholderColor = Pixel.InkSoft, errorPlaceholderColor = Pixel.InkSoft, disabledPlaceholderColor = off,
        focusedSupportingTextColor = Pixel.InkSoft, unfocusedSupportingTextColor = Pixel.InkSoft, errorSupportingTextColor = Pixel.Danger, disabledSupportingTextColor = off,
        cursorColor = Pixel.Blue, errorCursorColor = Pixel.Danger,
    )
    OutlinedTextField(
        value, onChange,
        modifier.semantics { if (error != null) error(error) },
        enabled = enabled, readOnly = readOnly, textStyle = body,
        label = label?.let { { Text(it, style = soft) } },
        placeholder = placeholder?.let { { Text(it, style = body) } },
        supportingText = (error ?: helper)?.let { { Text(it, style = soft) } },
        isError = error != null, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines, colors = colors,
    )
}
