package com.xiaozhangben.ledger.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.SectionCard
import kotlinx.coroutines.launch

private enum class PinDialog { NONE, SET_NEW, CONFIRM_NEW, VERIFY_TO_DISABLE }

@Composable
fun LockSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dialog by remember { mutableStateOf(PinDialog.NONE) }
    var firstPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.setBiometricAvailable(BiometricHelper.isAvailable(context))
    }

    LedgerScaffold(title = "密码与指纹", modifier = modifier, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "应用锁") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("密码锁", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (state.lockEnabled) "已开启，打开 App 需要输入密码" else "未开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.lockEnabled,
                        onCheckedChange = { checked ->
                            errorText = null
                            firstPin = ""
                            dialog = if (checked) PinDialog.SET_NEW else PinDialog.VERIFY_TO_DISABLE
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("指纹 / 面容解锁", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when {
                                !state.hasPin -> "请先设置密码锁"
                                !state.biometricAvailable -> "本机没有可用的生物识别"
                                else -> "验证通过后可直接进入"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.biometricEnabled,
                        enabled = state.hasPin && state.biometricAvailable,
                        onCheckedChange = { checked ->
                            scope.launch { viewModel.updateBiometric(checked) }
                        },
                    )
                }
            }

            SectionCard(title = "说明") {
                Text(
                    text = "密码只在本机做校验，保存的是加盐哈希值（PBKDF2），不会上传到任何服务器。" +
                        "忘记密码无法找回，只能清除应用数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    when (dialog) {
        PinDialog.NONE -> Unit

        PinDialog.SET_NEW -> PinInputDialog(
            title = "设置 $PIN_LENGTH 位数字密码",
            confirmText = "下一步",
            errorText = errorText,
            onConfirm = { pin ->
                if (pin.length != PIN_LENGTH) {
                    errorText = "请输入 $PIN_LENGTH 位数字"
                } else {
                    firstPin = pin
                    errorText = null
                    dialog = PinDialog.CONFIRM_NEW
                }
            },
            onDismiss = {
                dialog = PinDialog.NONE
                errorText = null
            },
        )

        PinDialog.CONFIRM_NEW -> PinInputDialog(
            title = "再次输入确认",
            confirmText = "完成",
            errorText = errorText,
            onConfirm = { pin ->
                if (pin != firstPin) {
                    errorText = "两次输入不一致，请重新设置"
                    dialog = PinDialog.SET_NEW
                } else {
                    scope.launch {
                        viewModel.applyNewPin(pin)
                        dialog = PinDialog.NONE
                        errorText = null
                        firstPin = ""
                    }
                }
            },
            onDismiss = {
                dialog = PinDialog.NONE
                errorText = null
                firstPin = ""
            },
        )

        PinDialog.VERIFY_TO_DISABLE -> PinInputDialog(
            title = "输入当前密码以关闭",
            confirmText = "关闭密码锁",
            errorText = errorText,
            onConfirm = { pin ->
                scope.launch {
                    if (viewModel.verify(pin)) {
                        viewModel.disableLock()
                        dialog = PinDialog.NONE
                        errorText = null
                    } else {
                        errorText = "密码不正确"
                    }
                }
            },
            onDismiss = {
                dialog = PinDialog.NONE
                errorText = null
            },
        )
    }
}

@Composable
private fun PinInputDialog(
    title: String,
    confirmText: String,
    errorText: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input -> value = input.filter { it.isDigit() }.take(PIN_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
