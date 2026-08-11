package com.passandnote.vault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.passandnote.vault.data.PasswordValidationResult
import com.passandnote.vault.ui.theme.VaultAccent
import com.passandnote.vault.ui.theme.VaultBackground
import com.passandnote.vault.ui.theme.VaultBorder
import com.passandnote.vault.ui.theme.VaultCard
import com.passandnote.vault.ui.theme.VaultDanger
import com.passandnote.vault.ui.theme.VaultField
import com.passandnote.vault.ui.theme.VaultMutedText
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText
import com.passandnote.vault.ui.theme.VaultSuccess
import com.passandnote.vault.ui.theme.VaultWarning

object VaultSpacing {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 44.dp
}

object VaultRadius {
    val sm = 10.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
}

enum class AppButtonKind { PRIMARY, SECONDARY }
enum class InfoCardKind { INFO, WARNING, SUCCESS }

@Composable
fun VaultScreenContainer(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(VaultBackground), contentAlignment = Alignment.TopCenter) {
        val columnModifier = Modifier
            .fillMaxWidth()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .navigationBarsPadding()
            .imePadding()
        Column(columnModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.fillMaxWidth().width(520.dp).padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xl),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(title, color = VaultPrimaryText, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = VaultAccent)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground),
            )
        },
    ) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@Composable
fun AppButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: AppButtonKind = AppButtonKind.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val primary = kind == AppButtonKind.PRIMARY
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(54.dp).alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(VaultRadius.md),
        border = if (primary) null else BorderStroke(1.dp, VaultBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) VaultAccent else VaultCard,
            contentColor = VaultPrimaryText,
            disabledContainerColor = if (primary) VaultAccent else VaultCard,
            disabledContentColor = VaultPrimaryText,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
        }
        if (loading || icon != null) Spacer(Modifier.width(VaultSpacing.sm))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun AppTextField(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = VaultMutedText) },
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = VaultPrimaryText),
            shape = RoundedCornerShape(VaultRadius.md),
            colors = vaultFieldColors(error != null),
            modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier.height(54.dp) else Modifier),
        )
        if (!error.isNullOrEmpty()) Text(error, style = MaterialTheme.typography.labelMedium, color = VaultDanger)
    }
}

@Composable
fun AppSecureField(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = VaultMutedText) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = VaultPrimaryText),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password", tint = VaultSecondaryText)
                }
            },
            shape = RoundedCornerShape(VaultRadius.md),
            colors = vaultFieldColors(error != null),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        )
        if (!error.isNullOrEmpty()) Text(error, style = MaterialTheme.typography.labelMedium, color = VaultDanger)
    }
}

@Composable
private fun vaultFieldColors(isError: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = VaultField,
    unfocusedContainerColor = VaultField,
    disabledContainerColor = VaultField,
    focusedBorderColor = if (isError) VaultDanger else VaultBorder,
    unfocusedBorderColor = if (isError) VaultDanger else VaultBorder,
    cursorColor = VaultAccent,
    focusedTextColor = VaultPrimaryText,
    unfocusedTextColor = VaultPrimaryText,
)

@Composable
fun InfoCard(kind: InfoCardKind, title: String? = null, message: String, modifier: Modifier = Modifier) {
    val tint = when (kind) {
        InfoCardKind.INFO -> VaultAccent
        InfoCardKind.WARNING -> VaultWarning
        InfoCardKind.SUCCESS -> VaultSuccess
    }
    val icon = when (kind) {
        InfoCardKind.INFO -> Icons.Rounded.Info
        InfoCardKind.WARNING -> Icons.Rounded.ErrorOutline
        InfoCardKind.SUCCESS -> Icons.Rounded.CheckCircleOutline
    }
    Row(
        modifier.fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(VaultRadius.md))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(VaultRadius.md))
            .padding(VaultSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            if (title != null) Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = VaultSecondaryText)
        }
    }
}

@Composable
fun StepProgressIndicator(currentStep: Int, totalSteps: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
        Text("Step $currentStep of $totalSteps", style = MaterialTheme.typography.labelMedium, color = VaultMutedText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            repeat(totalSteps) { index ->
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .background(if (index < currentStep) VaultAccent else VaultBorder, RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
fun PasswordChecklist(result: PasswordValidationResult, title: String = "Password strength") {
    Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
        Column(
            Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md)).padding(VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            RuleRow("At least 12 characters", result.hasMinimumLength)
            RuleRow("Uppercase letter", result.hasUppercase)
            RuleRow("Lowercase letter", result.hasLowercase)
            RuleRow("Number", result.hasNumber)
            RuleRow("Special character", result.hasSpecialCharacter)
        }
    }
}

@Composable
private fun RuleRow(title: String, valid: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (valid) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
            null,
            tint = if (valid) VaultSuccess else VaultMutedText,
            modifier = Modifier.size(20.dp),
        )
        Text(title, style = MaterialTheme.typography.bodyMedium, color = if (valid) VaultPrimaryText else VaultSecondaryText)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, large: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
        Text(
            title,
            style = if (large) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
            color = VaultPrimaryText,
        )
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = VaultSecondaryText)
    }
}

@Composable
fun CardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md))
            .border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md)),
        content = content,
    )
}

@Composable
fun VaultDivider() = HorizontalDivider(color = VaultBorder)

@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(VaultBackground).statusBarsPadding()) { content() }
    }
}

@Composable
fun ToolbarTextButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(title, color = if (enabled) VaultAccent else VaultMutedText) }
}

@Composable
fun CircleIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(52.dp).background(VaultAccent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = VaultAccent, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun ClickableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md))
            .border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md))
            .clickable(onClick = onClick).padding(VaultSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}
