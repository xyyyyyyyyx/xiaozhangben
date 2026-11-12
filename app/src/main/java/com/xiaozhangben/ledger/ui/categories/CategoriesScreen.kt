// 分类管理页面：支出/收入两个分类集合的排序、增删改与归档管理。
package com.xiaozhangben.ledger.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.data.db.DefaultData
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.toComposeColor
import kotlinx.coroutines.launch

/** 列表项：普通分类行或归档区标题。 */
private sealed interface CategoryListItem {
    data class Row(val category: Category, val index: Int) : CategoryListItem
    data object ArchivedHeader : CategoryListItem
}

private const val ICON_COLUMNS = 6

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var dialogCategory by remember { mutableStateOf<Category?>(null) }

    val type = if (state.selectedTab == 0) TxnType.EXPENSE else TxnType.INCOME
    val ofType = state.categories.filter { it.type == type }.sortedBy { it.sortOrder }
    val active = ofType.filter { !it.archived }
    val archived = ofType.filter { it.archived }

    val listItems = buildList {
        active.forEachIndexed { index, category -> add(CategoryListItem.Row(category, index)) }
        if (archived.isNotEmpty()) {
            add(CategoryListItem.ArchivedHeader)
            if (state.archivedExpanded) {
                archived.forEach { add(CategoryListItem.Row(it, -1)) }
            }
        }
    }

    LedgerScaffold(
        title = "分类管理",
        modifier = modifier,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dialogCategory = null
                    showDialog = true
                },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新建分类")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // material3 的 TabRow 第一个参数就是 selectedTabIndex（必填）。
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("支出") },
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("收入") },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "summary") {
                    Text(
                        text = "${type.label}分类共 ${active.size} 个（已归档 ${archived.size} 个）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }

                if (active.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "还没有${type.label}分类，点右下角「+」新建一个。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                items(items = listItems, key = { item ->
                    when (item) {
                        is CategoryListItem.Row -> "${type.name}-${item.category.id}"
                        CategoryListItem.ArchivedHeader -> "${type.name}-archived-header"
                    }
                }) { item ->
                    when (item) {
                        is CategoryListItem.ArchivedHeader -> ArchivedHeader(
                            count = archived.size,
                            expanded = state.archivedExpanded,
                            onClick = viewModel::toggleArchived,
                        )

                        is CategoryListItem.Row -> CategoryRow(
                            category = item.category,
                            archived = item.index < 0,
                            canMoveUp = item.index > 0,
                            canMoveDown = item.index >= 0 && item.index < active.lastIndex,
                            onMoveUp = { scope.launch { viewModel.moveCategory(item.category.id, -1) } },
                            onMoveDown = { scope.launch { viewModel.moveCategory(item.category.id, 1) } },
                            onEdit = {
                                dialogCategory = item.category
                                showDialog = true
                            },
                            onDelete = { scope.launch { viewModel.deleteCategory(item.category) } },
                            onRestore = { scope.launch { viewModel.setArchived(item.category.id, false) } },
                        )
                    }
                }

                val message = state.errorMessage
                if (message != null) {
                    item(key = "error") {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.consumeError() }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }

                item(key = "bottomSpace") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showDialog) {
        CategoryEditDialog(
            category = dialogCategory,
            type = type,
            existingNames = ofType.map { it.name },
            onDismiss = { showDialog = false },
            onSave = { name, icon, color ->
                scope.launch {
                    val editing = dialogCategory
                    val ok = if (editing == null) {
                        viewModel.addCategory(name, type, icon, color)
                    } else {
                        viewModel.updateCategory(editing, name, icon, color)
                    }
                    if (ok) showDialog = false
                }
            },
            onDelete = {
                val editing = dialogCategory
                if (editing != null) {
                    showDialog = false
                    scope.launch { viewModel.deleteCategory(editing) }
                }
            },
        )
    }

    val pending = state.pendingArchive
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingArchive,
            title = { Text("无法删除分类") },
            text = {
                Text(
                    text = buildString {
                        append("「${pending.name}」已有 ")
                        append(state.pendingArchiveUsage.ifEmpty { "若干" })
                        append("在用，删除会让这些记录变成未分类。建议改为归档，归档后不再出现在记账选择里，历史数据仍保留。")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { viewModel.setArchived(pending.id, true) } }) {
                    Text("归档")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingArchive) { Text("取消") }
            },
        )
    }
}

/** 单个分类一行：图标 + 名称 + 排序/编辑/删除操作。 */
@Composable
private fun CategoryRow(
    category: Category,
    archived: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !archived, onClick = onEdit)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryAvatar(icon = category.icon, color = category.color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (archived) "已归档" else "排序 ${category.sortOrder + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (archived) {
            TextButton(onClick = onRestore) { Text("恢复") }
        } else {
            // 四个图标按钮排一行，压到 34.dp 才不会把分类名挤没。
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "上移",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "下移",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 「已归档（N）」折叠标题。 */
@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已归档（$count）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }
}

/** 新建 / 编辑分类对话框。 */
@Composable
private fun CategoryEditDialog(
    category: Category?,
    type: TxnType,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var icon by remember(category) { mutableStateOf(category?.icon ?: DefaultData.categoryIcons.first()) }
    var color by remember(category) { mutableLongStateOf(category?.color ?: DefaultData.categoryColors.first()) }
    var localError by remember(category) { mutableStateOf<String?>(null) }

    val editing = category != null

    fun submit() {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> localError = "请填写分类名称"
            existingNames.any { it == trimmed && it != category?.name } -> localError = "已有同名分类：$trimmed"
            else -> {
                localError = null
                onSave(trimmed, icon, color)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "编辑${type.label}分类" else "新建${type.label}分类") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = null
                    },
                    label = { Text("分类名称") },
                    singleLine = true,
                    isError = localError != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "图标",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 手动分行：对话框内不引入 Lazy 网格，布局更稳定。
                DefaultData.categoryIcons.chunked(ICON_COLUMNS).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowIcons.forEach { candidate ->
                            IconCell(
                                icon = candidate,
                                selected = candidate == icon,
                                selectedColor = color,
                                onClick = { icon = candidate },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 最后一行不足时用空白占位，保证图标等宽对齐。
                        repeat(ICON_COLUMNS - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DefaultData.categoryColors.forEach { value ->
                        ColorDot(selected = value == color, color = value, onClick = { color = value })
                    }
                }

                if (localError != null) {
                    Text(
                        text = localError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text("保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 图标网格里的一格。 */
@Composable
private fun IconCell(
    icon: String,
    selected: Boolean,
    selectedColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    selectedColor.toComposeColor().copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, fontSize = 18.sp)
    }
}

/** 颜色选择小圆点。 */
@Composable
private fun ColorDot(selected: Boolean, color: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.toComposeColor())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选颜色",
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
