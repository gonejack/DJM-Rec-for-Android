# Kotlin 代码排版规则（唯一来源）

本项目**不手工调整换行和缩进**。排版只由两个文件决定，它们是唯一事实来源：

| 文件 | 生效范围 |
|---|---|
| [`.editorconfig`](../.editorconfig) | Android Studio / IntelliJ 的 `Reformat Code`（`Cmd/Ctrl + Alt + L`）、ktfmt IDE 插件 |
| [`app/build.gradle.kts`](../app/build.gradle.kts) 的 `ktfmt { }` 块 | 命令行 `./gradlew` 任务、CI |

两者是同一套参数的两种写法，改规则时必须**同时**改，否则会出现「IDE 格式化后 CI 报错」。

---

## 具体规则

| 项 | 取值 | 说明 |
|---|---|---|
| 缩进 | 4 空格 | 与 `kotlin.code.style=official` 一致。**不要**用 `googleStyle()`（2 空格） |
| 续行缩进 | 4 空格 | `Modifier` 链断开时 `.call()` 相对上一行 +4 |
| 行宽 | 160 | 超过才拆行 |
| 参数换行 | `normal` | 能一行放下就**保持一行**；放不下才**每个参数独占一行** |
| 方法链换行 | `normal` | `Modifier.fillMaxSize().padding(...)` 短就一行；长就每行一个 `.call()` |
| 手工断行 | 不保留 | `ij_kotlin_keep_line_breaks = false`，工具完全接管 |
| 尾随逗号 | 不使用 | 声明和调用处都不加（`TrailingCommaManagementStrategy.NONE`） |
| 星号导入 | 禁止 | 显式列每个 import |
| 未使用 import | 自动删除 | `removeUnusedImports = true` |
| 文件编码 | UTF-8，**无 BOM** | 带 BOM 会让 ktfmt 解析失败（`1:1: error: Expecting a top level declaration`） |
| 换行符 | LF | |
| 行尾空格 | 删除 | |
| 文件末尾 | 保留一个换行 | |

### 换行判定示例

```kotlin
// ✅ 一行放得下（86 字符）→ 保持一行
Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

// ❌ 不要手动拆成多行（工具会合并回来）
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {

// ✅ 参数加起来超 160 → 每个参数独占一行，但单个参数内部仍保持一行
Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
) {

// ✅ 方法链能一行就一行
modifier = Modifier.weight(1f).padding(horizontal = 12.dp)

// ✅ 方法链超宽 → 每行一个 .call()，续行 +4
modifier = Modifier
    .fillMaxWidth()
    .heightIn(min = 72.dp)
    .padding(12.dp)
```

---

## 常用命令

```bash
# 只检查，不改文件（适合 CI / 提交前自查）
./gradlew :app:ktfmtCheck

# 就地重排全部 Kotlin 源码
./gradlew :app:ktfmtFormat
```

### 与构建的关系（已启用门禁）

ktfmt 插件会把 `ktfmtCheck*` 挂到 `check` 上，所以 `./gradlew check` 会在排版不合规时失败。
全仓库已经统一格式化，因此门禁保持开启；临时绕过可以用 `./gradlew check -x ktfmtCheck`。

> ⚠️ 注意 `.editorconfig` 与 `app/build.gradle.kts` 的 `maxWidth` **必须一致**，否则
> IDE 格式化（读 `.editorconfig`）和命令行格式化（读 Gradle）会反复互相改写。

Android Studio 里装 **ktfmt** 插件并勾选 `Enable ktfmt` 后，`Reformat Code` 会直接调用 ktfmt，
和命令行输出完全一致；不装也可以，此时由 `.editorconfig` 让内置格式化器近似同样的结果。

---

## 已知差异（ktfmt 做不到的事）

ktfmt 是「零配置」格式化器，只有 `style / maxWidth / blockIndent / continuationIndent` 几个旋钮。
以下偏好**无法**用 ktfmt 表达，本项目统一按 ktfmt 的行为来：

1. **「短调用也强制多行」做不到。** `Column(modifier = Modifier.weight(1f)) {` 永远会被合成一行。
   如果确实想要「每个参数永远独占一行」，只能把 `.editorconfig` 的
   `ij_kotlin_call_parameters_wrap` 改成 `on_every_item`，但那样 IDE 和 ktfmt 会互相打架。
2. **空行数量**由 ktfmt 决定（连续空行最多保留 2 个），不像 IntelliJ 那样可配。
3. **注解位置**（`@Composable`、`@Preview` 是否独占一行）由 ktfmt 决定。

---

## 历史遗留

`ui/LibraryScreen.kt` 和 `ui/SettingsScreen.kt` 曾经带 UTF-8 BOM，导致 ktfmt 无法解析。
BOM 已于本次配置时移除；`.editorconfig` 的 `charset = utf-8` 会阻止 IDE 再次写入 BOM。
