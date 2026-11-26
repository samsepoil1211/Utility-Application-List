package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.icons.InstrumentIcons
import com.example.ui.motion.InstrumentMotion
import com.example.ui.motion.pressSpring
import com.example.ui.theme.LocalInstrumentColors

@Composable
fun AmberPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }

    Button(
        onClick = {
            InstrumentMotion.lightHaptic(context)
            onClick()
        },
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.amber,
            contentColor = colors.onAmber,
            disabledContainerColor = colors.line,
            disabledContentColor = colors.muted
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressSpring(interaction)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.onAmber
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun QuietOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val fg = if (danger) colors.coral else colors.text
    val border = if (danger) colors.coral.copy(alpha = 0.55f) else colors.line

    OutlinedButton(
        onClick = {
            InstrumentMotion.lightHaptic(context)
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.panel,
            contentColor = fg,
            disabledContentColor = colors.muted
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .pressSpring(interaction)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun InstrumentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true
) {
    val colors = LocalInstrumentColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = placeholder?.let { { Text(it, color = colors.muted.copy(alpha = 0.7f)) } },
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) colors.coral else colors.muted
                )
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) InstrumentIcons.EyeOff else InstrumentIcons.Eye,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.amber,
            unfocusedBorderColor = colors.line,
            errorBorderColor = colors.coral,
            focusedLabelColor = colors.amber,
            unfocusedLabelColor = colors.muted,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            cursorColor = colors.amber,
            focusedContainerColor = colors.panel,
            unfocusedContainerColor = colors.panel,
            disabledContainerColor = colors.panel.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun InstrumentToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = LocalInstrumentColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.text else colors.muted
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAmber,
                checkedTrackColor = colors.amber,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.panelElevated,
                uncheckedBorderColor = colors.line
            )
        )
    }
}

@Composable
fun InstrumentActionRow(
    title: String,
    subtitle: String? = null,
    valueText: String? = null,
    valueColor: Color? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) colors.coral else colors.text
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = valueColor ?: colors.muted
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                imageVector = InstrumentIcons.ChevronRight,
                contentDescription = null,
                tint = if (danger) colors.coral else colors.muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun InstrumentSectionPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalInstrumentColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.muted,
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(16.dp))
                .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = LocalInstrumentColors.current.line.copy(alpha = 0.8f)
    )
}

@Composable
fun KeyDerivationBanner(label: String, modifier: Modifier = Modifier) {
    val colors = LocalInstrumentColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panelElevated, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colors.amber
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text
        )
    }
}
