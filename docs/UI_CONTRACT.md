# 小账本 UI 开发约定（所有界面代码必须遵守）

本文件是共享契约。**写任何界面代码之前，先完整读一遍本文件，并读一遍下面列出的源文件确认签名。**

## 0. 绝对规则

1. 只允许**新增**你自己负责的 `ui/<功能>/` 目录下的文件。**不要修改**任何共享文件：
   - `domain/model/Models.kt`、`domain/Money.kt`、`domain/DateUtils.kt`、`domain/StatsCalculator.kt`
   - `data/repo/LedgerRepository.kt`、`data/prefs/*`、`data/backup/*`
   - `ui/components/Common.kt`、`ui/components/Charts.kt`、`ui/theme/*`、`ui/UiUtils.kt`
   - `ui/AppViewModelProvider.kt`、`ui/navigation/AppNavigation.kt`、`MainActivity.kt`
2. **不要新增任何 Gradle 依赖**。可用依赖仅限：Compose BOM 2024.12.01（material3、ui、foundation、runtime）、navigation-compose、lifecycle-viewmodel-compose、lifecycle-runtime-compose、Room、DataStore、biometric、coroutines。**没有** material-icons-extended。
3. 界面文案全部用**简体中文**，代码注释也用中文。
4. 不要使用任何 `@Preview`（当前工程未配置预览依赖的额外参数，且无助于编译）。
5. 金额一律是 `Long`，单位「分」。显示用 `Money.format(...)` / `Money.formatSigned(...)` / `Money.formatCompact(...)`，解析用户输入用 `Money.parse(text): Long?`。
6. 时间一律是 `Long`（epoch 毫秒）。显示用 `DateUtils.*`。
7. 图标只用 **material-icons-core** 里存在的这些：
   `Icons.Default.Add / Edit / Delete / Check / Close / Search / Settings / MoreVert / Lock / Refresh / Warning / Info / KeyboardArrowDown / KeyboardArrowUp / Star / Person / Home / Clear / Done / Menu / Share / List / Notifications / DateRange`
   以及 `Icons.AutoMirrored.Filled.ArrowBack / KeyboardArrowRight / List`。
   **不要**用 `Fingerprint`、`AccountBalance`、`PieChart`、`TrendingUp` 等 extended 图标 —— 不存在会编译失败。需要图标感时用 emoji 文本（如 `Text("🔒")`）。
8. 顶部栏统一用 `LedgerScaffold`（已封装 `TopAppBar` 与返回按钮），不要自己写 `Scaffold` + `TopAppBar`（`TopAppBar` 需要 `@OptIn(ExperimentalMaterial3Api::class)`，`LedgerScaffold` 内部已处理）。

## 1. 必须先读的文件（确认精确签名）

```
app/src/main/java/com/xiaozhangben/ledger/domain/model/Models.kt
app/src/main/java/com/xiaozhangben/ledger/domain/Money.kt
app/src/main/java/com/xiaozhangben/ledger/domain/DateUtils.kt
app/src/main/java/com/xiaozhangben/ledger/domain/StatsCalculator.kt
app/src/main/java/com/xiaozhangben/ledger/data/repo/LedgerRepository.kt
app/src/main/java/com/xiaozhangben/ledger/data/prefs/AppPreferences.kt
app/src/main/java/com/xiaozhangben/ledger/data/backup/CsvCodec.kt
app/src/main/java/com/xiaozhangben/ledger/data/backup/BackupManager.kt
app/src/main/java/com/xiaozhangben/ledger/ui/components/Common.kt
app/src/main/java/com/xiaozhangben/ledger/ui/components/Charts.kt
app/src/main/java/com/xiaozhangben/ledger/ui/theme/Color.kt
```

包名根：`com.xiaozhangben.ledger`。

## 2. 共享组件（`com.xiaozhangben.ledger.ui.components`）

```kotlin
@Composable fun CategoryAvatar(icon: String, color: Long, modifier: Modifier = Modifier, size: Dp = 40.dp)

@Composable fun AmountText(
    amount: Long,                 // 正数分值
    type: TxnType,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    showSign: Boolean = true,
)

@Composable fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
)

@Composable fun EmptyState(
    icon: String, title: String, modifier: Modifier = Modifier,
    subtitle: String? = null, action: (@Composable () -> Unit)? = null,
)

@Composable fun LedgerScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
)

@Composable fun ConfirmDialog(
    title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    confirmText: String = "确定", dismissText: String = "取消", destructive: Boolean = false,
)

@Composable fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface)

@Composable fun SettingRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    trailingText: String? = null, showArrow: Boolean = true, onClick: (() -> Unit)? = null,
)
```

图表（`ui.components`）：

```kotlin
@Composable fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier)
@Composable fun MonthlyTrendChart(points: List<MonthlyPoint>, modifier: Modifier = Modifier, chartHeight: Dp = 156.dp)
@Composable fun CategoryDonutChart(slices: List<CategorySlice>, modifier: Modifier = Modifier, size: Dp = 200.dp)
@Composable fun DailyBarChart(values: List<Long>, modifier: Modifier = Modifier, chartHeight: Dp = 90.dp, barColor: Color = LedgerColors.expense, showAxis: Boolean = true)
@Composable fun RatioBar(ratio: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, trackColor: Color = MaterialTheme.colorScheme.surfaceVariant)
```

主题语义色（`com.xiaozhangben.ledger.ui.theme.LedgerColors`）：`LedgerColors.income`、`LedgerColors.expense`、`LedgerColors.warning`（都是 `@Composable` getter）。
工具（`com.xiaozhangben.ledger.ui`）：`Long.toComposeColor(): Color`。

## 3. 数据契约

`LedgerRepository`（通过 ViewModel 构造函数注入，不要自己 new）关键成员：

```kotlin
val settings: Flow<AppSettings>
val categories: Flow<List<Category>>          // 含 archived
val accounts: Flow<List<Account>>
val transactions: Flow<List<Transaction>>     // 已按 occurredAt 倒序，且已带上 categoryName/accountName 等
val budgets: Flow<List<Budget>>
val recurringRules: Flow<List<RecurringRule>>

suspend fun addTransaction(t: Transaction): Long
suspend fun updateTransaction(t: Transaction)
suspend fun deleteTransaction(id: Long)
suspend fun findTransaction(id: Long): Transaction?

suspend fun addCategory(c: Category): Long
suspend fun updateCategory(c: Category)
suspend fun setCategoryArchived(id: Long, archived: Boolean)
suspend fun moveCategory(id: Long, delta: Int)      // delta = -1 上移 / +1 下移
suspend fun deleteCategory(id: Long): DeleteResult  // DeleteResult.Deleted | DeleteResult.InUse(count, recurringCount)

suspend fun addAccount(a: Account): Long
suspend fun updateAccount(a: Account)
suspend fun setAccountArchived(id: Long, archived: Boolean)
suspend fun deleteAccount(id: Long): DeleteResult

suspend fun saveBudget(categoryId: Long?, amount: Long)   // categoryId = null 表示总预算；amount <= 0 表示删除该预算
suspend fun setBudgetEnabled(id: Long, enabled: Boolean)
suspend fun deleteBudget(id: Long)

suspend fun addRecurring(r: RecurringRule): Long
suspend fun updateRecurring(r: RecurringRule)
suspend fun deleteRecurring(id: Long)
suspend fun findRecurring(id: Long): RecurringRule?
suspend fun postRecurringNow(r: RecurringRule)      // 立即记一笔并推进下次时间
suspend fun applyDueRecurring(now: Long = System.currentTimeMillis()): Int

suspend fun exportCsv(): String
suspend fun importCsv(text: String): ImportResult
suspend fun clearTransactions()
suspend fun resetEverything()
suspend fun transactionCount(): Int
suspend fun categoryUsage(id: Long): Int
suspend fun accountUsage(id: Long): Int
suspend fun categoriesOnce(): List<Category>
suspend fun accountsOnce(): List<Account>
suspend fun categoriesOf(type: TxnType): List<Category>   // 仅未归档

// 安全与外观（后续补充，设置页/锁屏使用）
suspend fun verifyPin(pin: String): Boolean
suspend fun setPin(pin: String)
suspend fun setLockEnabled(enabled: Boolean)
suspend fun setBiometricEnabled(enabled: Boolean)
suspend fun setThemeMode(mode: ThemeMode)
suspend fun clearPin()
```

`BackupManager`：`writeText(uri: Uri, text: String)`、`readText(uri: Uri): String`、`suggestedFileName(prefix: String): String`。
`CsvCodec`：`encode(list): String`、`decode(text): List<CsvRow>`、常量 `HEADER`。

`AppSettings`：`seeded, lockEnabled, biometricEnabled, pinSalt, pinHash, themeMode: ThemeMode, lastExportAt, lastImportAt, hasPin`。

## 4. ViewModel 规范

固定构造签名（`AppViewModelProvider` 已按这些签名注册，写错会编译失败）：

```kotlin
class HomeViewModel(repository: LedgerRepository) : ViewModel()
class RecordsViewModel(repository: LedgerRepository) : ViewModel()
class StatsViewModel(repository: LedgerRepository) : ViewModel()
class AccountsViewModel(repository: LedgerRepository) : ViewModel()
class CategoriesViewModel(repository: LedgerRepository) : ViewModel()
class BudgetViewModel(repository: LedgerRepository) : ViewModel()
class RecurringViewModel(repository: LedgerRepository) : ViewModel()
class SettingsViewModel(repository: LedgerRepository, backupManager: BackupManager) : ViewModel()
class EditorViewModel(repository: LedgerRepository) : ViewModel()
```

写法：

```kotlin
class XxxViewModel(private val repository: LedgerRepository) : ViewModel() {
    private val _state = MutableStateFlow(XxxUiState())
    val state: StateFlow<XxxUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.transactions.collect { list -> _state.update { it.copy(transactions = list) } }
        }
    }
}
```

界面里取：

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
```

需要的 import：`androidx.lifecycle.compose.collectAsStateWithLifecycle`、`androidx.lifecycle.viewModelScope`、`androidx.lifecycle.ViewModel`。

## 5. 界面函数签名（导航层会按这些签名调用，必须一字不差）

```kotlin
// ui/home/HomeScreen.kt
@Composable fun HomeScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/records/RecordsScreen.kt
@Composable fun RecordsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/stats/StatsScreen.kt
@Composable fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/accounts/AccountsScreen.kt
@Composable fun AccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/categories/CategoriesScreen.kt
@Composable fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/budget/BudgetScreen.kt
@Composable fun BudgetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/recurring/RecurringScreen.kt
@Composable fun RecurringScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringViewModel = viewModel(factory = AppViewModelFactory),
)

// ui/settings/SettingsScreen.kt
@Composable fun SettingsScreen(
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
)
```

`AppViewModelFactory` 从 `com.xiaozhangben.ledger.ui.AppViewModelProvider` 导入：
`import com.xiaozhangben.ledger.ui.AppViewModelFactory`
`viewModel` 从 `androidx.lifecycle.viewmodel.compose.viewModel` 导入。

## 6. 代码风格

- 每个文件顶部写一行中文用途注释。
- 状态类命名为 `XxxUiState`，与被调用方约定的字段不要改。
- `viewModelScope.launch { ... }` 里调用 `repository` 的挂起函数。
- 列表用 `LazyColumn`；分组可用 `stickyHeader`（需 `@OptIn(ExperimentalFoundationApi::class)`，或者干脆用普通 `item` 做分组标题，更稳）。
- 金额输入框：`keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)`。
- 不要吞掉异常：写操作用 `runCatching { }` 并把失败信息放进 UI 状态里显示（用 `Snackbar` 或 `Text`）。
