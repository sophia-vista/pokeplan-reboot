package app.pokeplan.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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

/*
 * The text field of the control system (the sibling of [PixelKey]; states in docs/ui-ux-brief.md, section 5.1), drawn like a key: a
 * stair-cornered 2 dp outline round a panel face, no rounding and no floating label.
 *
 *   state     face       outline        text                  notes
 *   rest      panel      border         ink                   placeholder in ink-soft
 *   focused   panel      blue           ink                   the same blue as a key's focus ring
 *   error     panel      danger         ink                   [error] replaces [helper], is announced, and says what to do
 *   disabled  shade      border at half ink-soft at 55%       not editable, not focusable
 *   read-only panel      border         ink                   selectable, not editable
 *
 * A label is a small line above the field (use it when the field is alone on a line); a placeholder shows the example inside it
 * (use it beside a button). One line of text is centred in the field's 48 dp, so a field and a key beside it share a centre line.
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val off = Pixel.InkSoft.copy(alpha = 0.55f)
    val edge = when {
        error != null -> Pixel.Danger
        focused -> Pixel.Blue
        !enabled -> Pixel.Border.copy(alpha = 0.5f)
        else -> Pixel.Border
    }
    val body = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) Pixel.Ink else off, baselineShift = androidx.compose.ui.text.style.BaselineShift.None)
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
                    // A text field draws its own text, which ignores the style's baseline shift, so the same lift is made here.
                    Box(Modifier.offset(y = (-2.25).dp)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = body.copy(color = Pixel.InkSoft))
                        inner()
                    }
                }
            },
        )
        (error ?: helper)?.let { Text(it, style = soft, color = if (error != null) Pixel.Danger else Pixel.InkSoft) }
    }
}
