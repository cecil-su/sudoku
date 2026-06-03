package com.sudoku.game.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sudoku.game.model.AiProvider
import java.util.UUID

private data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val keyword: String,
    val models: List<String>
)

private val PRESETS = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com", "deepseek", listOf("deepseek-chat", "deepseek-reasoner")),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "openai", listOf("gpt-4o-mini", "gpt-4o"))
)

/** Model suggestions for the given base URL: the matching preset's models, or all
 *  preset models when nothing matches. */
private fun modelSuggestions(baseUrl: String): List<String> =
    PRESETS.firstOrNull { baseUrl.contains(it.keyword, ignoreCase = true) }?.models
        ?: PRESETS.flatMap { it.models }.distinct()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    existing: AiProvider?,
    onSave: (AiProvider) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var model by remember { mutableStateOf(existing?.activeModel ?: "") }
    var showKey by remember { mutableStateOf(false) }

    val suggestions = modelSuggestions(baseUrl)
    val canSave = name.isNotBlank() && baseUrl.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "添加 Provider" else "编辑 Provider") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick-fill presets — one tap fills name + base URL (+ a default model).
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PRESETS.forEach { preset ->
                    AssistChip(
                        onClick = {
                            name = preset.name
                            baseUrl = preset.baseUrl
                            if (model.isBlank()) model = preset.models.first()
                        },
                        label = { Text("快填 ${preset.name}") }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                supportingText = { Text("OpenAI 兼容，如 https://api.deepseek.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型 ID") },
                supportingText = { Text("下方建议可点选，或手动输入") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    FilterChip(
                        selected = suggestion == model,
                        onClick = { model = suggestion },
                        label = { Text(suggestion) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val models = (existing?.models.orEmpty() + model)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    onSave(
                        AiProvider(
                            id = id,
                            name = name.trim(),
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            models = models,
                            activeModel = model.trim().ifBlank { null }
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }

            if (existing != null) {
                OutlinedButton(
                    onClick = { onDelete(id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.height(0.dp))
                    Text("  删除")
                }
            }
        }
    }
}
