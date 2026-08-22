# Material UI Refresh Implementation Plan

**Goal:** 使用现有 Material Components 重做管理首页及核心配置页面，并稳定恢复“普通点击执行、长按设置管理”的双入口。

**Architecture:** 保留 Java 业务层与所有持久化模型，仅替换主题资源、XML 布局和少量页面状态绑定。静态设置快捷方式继续路由到 MainActivity，动态快捷刷新只管理 deeplink 项，避免重复设置入口。

**Tech Stack:** Android XML、Java 8、Material Components 1.11、AppCompat、RecyclerView、JUnit 4、Android Lint。

---

### Task 1: Material 设计令牌与组件样式

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/res/color/button_text_danger.xml`

1. 增加页面背景、卡片、正文、辅助文字、描边、成功、警告和危险色。
2. 将应用主题配置为 MaterialComponents 浅色无 ActionBar，并设置状态栏、导航栏和默认字体颜色。
3. 增加主按钮、描边按钮、文字按钮、危险按钮、卡片和输入框公共样式。
4. 运行 `git diff --check`，预期无 XML 空白错误。

### Task 2: 管理首页与规则卡

**Files:**
- Modify: `app/src/main/res/layout/activity_rule_list.xml`
- Modify: `app/src/main/res/layout/item_rule.xml`
- Modify: `app/src/main/java/com/autojump/app/RuleListActivity.java`
- Modify: `app/src/main/java/com/autojump/app/adapter/RuleAdapter.java`

1. 用 MaterialToolbar、概览卡、主次按钮、RecyclerView 和空状态重建首页布局，保留原按钮与列表 ID。
2. 用 MaterialCardView、Chip、MaterialSwitch 和文字按钮重建规则项。
3. 在 RuleListActivity 绑定 Toolbar、概览与空状态；每次刷新时统计规则和服务状态。
4. 让空状态按钮与顶部新增按钮复用同一个新建规则入口。
5. 编译 Java 与资源，预期无缺失 ID 或类型错误。

### Task 3: 恢复并稳定长按设置快捷方式

**Files:**
- Modify: `app/src/main/java/com/autojump/app/MainActivity.java`
- Modify: `app/src/main/java/com/autojump/app/util/ShortcutUtil.java`
- Modify: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/res/values/strings.xml`

1. 保留静态 `settings` 快捷方式及 `open_settings=true` 路由。
2. 在 MainActivity 初始化时刷新动态 deeplink 快捷项。
3. 从动态列表移除重复的设置项，并为静态快捷方式预留一个系统名额。
4. 确认普通 ACTION_MAIN 不携带设置参数时仍执行规则/默认 deeplink。
5. 运行现有单元测试和 Lint，预期入口改造不影响自动化核心。

### Task 4: 核心配置页面套用 Material 组件

**Files:**
- Modify: `app/src/main/res/layout/activity_rule_edit.xml`
- Modify: `app/src/main/res/layout/item_condition.xml`
- Modify: `app/src/main/res/layout/item_action.xml`
- Modify: `app/src/main/res/layout/activity_click_action_edit.xml`
- Modify: `app/src/main/res/layout/activity_permission_guide.xml`

1. 按“基本信息、条件、动作、安全设置”将规则编辑页分成 Material 卡片。
2. 将条件和动作项改成紧凑卡片，保留编辑、排序和删除按钮 ID。
3. 将点击动作页按服务状态、执行参数、控件定位、录制分组，所有 EditText ID 保持不变。
4. 将权限页按自动点击、当前任务、运行记录、可选权限、系统设置分组。
5. 检查 48dp 触摸区域、危险操作颜色、大字体换行和滚动完整性。

### Task 5: 验证、文档、APK 与提交

**Files:**
- Modify: `README.md`
- Modify: `docs/DEVICE_VALIDATION.md`

1. 运行 `bash docker/run_remote_gradle.sh clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
2. 预期 36 项单元测试通过、Lint 零错误、Debug APK 构建成功。
3. 校验 APK applicationId 仍为 `com.autojump.app`，开发签名指纹保持不变。
4. 扫描源码、Git 提交和不可达对象，确认不存在用户要求删除的旧助手或厂商痕迹。
5. 更新 README 与真机验收清单，生成独立 UI 调试 APK。
6. 提交并推送到 `refactor/accessibility-click` 分支；设备连接后补做普通点击、长按设置、首页和大字体截图验收。
