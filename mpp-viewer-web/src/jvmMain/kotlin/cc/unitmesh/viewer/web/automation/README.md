# WebView Automation Testing Framework

This directory contains a modular testing framework for WebView Inspect功能的自动化测试框架。

## 📁 文件结构

```
automation/
├── TestModels.kt           # 测试数据模型 (TestResult, TestCategory)
├── TestHelper.kt           # DOM树操作辅助函数
└── WebViewTestRunner.kt    # 测试执行器
```

## 🚀 快速开始

### 运行测试

```bash
./gradlew :mpp-viewer-web:run
```

测试会自动：
1. 初始化 KCEF (Chromium Embedded Framework)
2. 加载测试页面 (`test-shadow-dom.html`)
3. 注入 JavaScript Bridge
4. 执行所有测试
5. 显示结果汇总
6. 5秒后自动退出

### 查看测试结果

测试输出格式：
```
╔══════════════════════════════════════════════════════════════╗
║        WebView Inspect Automation Test Suite                   ║
╚══════════════════════════════════════════════════════════════╝

BRIDGE_COMMUNICATION
  ✓ JS Bridge Availability (302 ms)
  ✓ Native Bridge Callback (505 ms)

DOM_INSPECTION
  ✓ Enable Inspect Mode (506 ms)
  ✗ Refresh DOM Tree (2503 ms)
  ✓ Highlight Element (501 ms)

...

╔══════════════════════════════════════════════════════════════╗
║                     TEST RESULTS SUMMARY                       ║
╠══════════════════════════════════════════════════════════════╣
║ ✓ BRIDGE_COMMUNICATION   2/2  ████████████████████ 100% ║
║ ✗ DOM_INSPECTION         2/3  █████████████░░░░░░░  66% ║
...
║ TOTAL: 10/13 tests  (76%)  ✗ FAILED                            ║
╚══════════════════════════════════════════════════════════════╝
```

## 📝 添加新测试

### 1. 在 WebViewTestRunner.kt 中添加测试

```kotlin
results.add(runTest("Test Name", TestCategory.DOM_INSPECTION) {
    // 执行测试逻辑
    bridge.executeJavaScript?.invoke("""
        // JavaScript代码
        console.log('Testing...');
    """.trimIndent())
    
    delay(500) // 等待JavaScript执行
    
    // 返回测试结果
    val passed = /* 验证逻辑 */
    passed
})
```

### 2. 使用 TestHelper 辅助函数

```kotlin
import cc.unitmesh.viewer.web.automation.TestHelper

// 统计DOM树元素数量
val count = TestHelper.countAllElements(domTree)

// 收集Shadow DOM宿主
val shadowHosts = TestHelper.collectShadowHosts(domTree)

// 查找特定元素
val element = TestHelper.findElement(domTree, "#my-element")

// 打印树结构（调试用）
TestHelper.printDOMTreeSummary(domTree, maxDepth = 3)
```

### 3. 添加新的测试类别

在 `TestModels.kt` 中：

```kotlin
enum class TestCategory {
    BRIDGE_COMMUNICATION,
    DOM_INSPECTION,
    SHADOW_DOM,
    USER_INTERACTION,
    MUTATION_OBSERVER,
    GENERAL,
    YOUR_NEW_CATEGORY  // 添加新类别
}
```

## 🔧 核心API

### TestHelper

| 方法 | 描述 |
|------|------|
| `countAllElements(element)` | 递归统计DOM树中的元素总数 |
| `collectShadowHosts(element)` | 收集所有Shadow DOM宿主元素 |
| `countShadowElements(element)` | 统计Shadow DOM内的元素数量 |
| `findElement(root, selector)` | 通过选择器查找元素 |
| `getElementsByTag(root, tagName)` | 获取指定标签的所有元素 |
| `validateDOMTree(root)` | 验证DOM树结构完整性 |
| `printDOMTreeSummary(element, depth, maxDepth)` | 打印树结构（调试） |

### WebViewTestRunner

```kotlin
class WebViewTestRunner(private val bridge: JvmWebEditBridge) {
    suspend fun runTests(): List<TestResult>
}
```

主要方法：
- `runTests()`: 执行完整测试套件
- `runTest(name, category, test)`: 执行单个测试
- `printSummary(results)`: 打印结果汇总

## 🧪 测试模式

### 同步测试
```kotlin
results.add(runTest("Sync Test", TestCategory.GENERAL) {
    val result = bridge.isSelectionMode.value
    result == true
})
```

### 异步测试
```kotlin
results.add(runTest("Async Test", TestCategory.DOM_INSPECTION) {
    bridge.refreshDOMTree()
    delay(2000) // 等待异步操作完成
    val tree = bridge.domTree.value
    tree != null
})
```

### JavaScript交互测试
```kotlin
results.add(runTest("JS Interaction", TestCategory.USER_INTERACTION) {
    bridge.executeJavaScript?.invoke("""
        const el = document.getElementById('test-element');
        el.style.color = 'red';
    """.trimIndent())
    delay(300)
    true
})
```

## 🐛 调试技巧

### 1. 添加调试输出

```kotlin
results.add(runTest("Debug Test", TestCategory.DOM_INSPECTION) {
    val tree = bridge.domTree.value
    print("  → DEBUG: tree=$tree, children=${tree?.children?.size}".padEnd(80))
    tree != null
})
```

### 2. 打印DOM树结构

```kotlin
val tree = bridge.domTree.value
if (tree != null) {
    TestHelper.printDOMTreeSummary(tree, maxDepth = 5)
}
```

输出示例：
```
- body
  - div#container
    ⚡- div#shadow-host [SHADOW HOST]
      🔒- div.shadow-content [in shadow]
        - button
```

### 3. 查看日志文件

```bash
# 应用日志
tail -f ~/.autodev/logs/autodev-app.log

# 错误日志
tail -f ~/.autodev/logs/autodev-app-error.log
```

## 📊 测试覆盖率

当前测试覆盖的功能：

- ✅ JavaScript Bridge 通信
- ✅ DOM 树构建与遍历
- ✅ 元素高亮显示
- ✅ 元素滚动定位
- ✅ 检查模式启用/禁用
- ✅ DOM 变更监听
- ⚠️ Shadow DOM 检测（部分）
- ⚠️ 元素选择回调（部分）

## 🎯 性能基准

| 测试类别 | 测试数量 | 平均耗时 |
|---------|---------|---------|
| BRIDGE_COMMUNICATION | 2 | ~400ms |
| DOM_INSPECTION | 3 | ~1000ms |
| SHADOW_DOM | 2 | ~250ms |
| USER_INTERACTION | 2 | ~650ms |
| MUTATION_OBSERVER | 2 | ~1000ms |
| GENERAL | 2 | ~300ms |

## 🔄 CI/CD 集成

### 退出码

测试框架会根据结果返回不同的退出码：

- `0`: 所有测试通过
- `1`: 部分测试失败
- `2`: 测试执行出错

### 使用示例

```bash
#!/bin/bash

./gradlew :mpp-viewer-web:run

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "✓ All tests passed"
elif [ $EXIT_CODE -eq 1 ]; then
    echo "✗ Some tests failed"
    exit 1
else
    echo "✗ Test execution error"
    exit 2
fi
```

## 📚 相关文档

- [测试总结文档](../AUTOMATION_TEST_SUMMARY.md)
- [WebView调试指南](../../docs/features/webview-debug.md)
- [KCEF集成文档](../../docs/kmp/kcef-integration.md)

## 🤝 贡献指南

添加新测试时请遵循：

1. **测试命名**: 使用描述性名称，如 "Verify Element Selection"
2. **测试分类**: 选择合适的 TestCategory
3. **等待时间**: JavaScript操作后添加适当的 delay
4. **断言清晰**: 测试逻辑要简单明了
5. **错误处理**: 使用 try-catch 处理可能的异常
6. **调试信息**: 失败时输出有用的调试信息

## 📧 联系方式

如有问题或建议，请：
- 提交 Issue
- 发起 Pull Request
- 查看项目文档
