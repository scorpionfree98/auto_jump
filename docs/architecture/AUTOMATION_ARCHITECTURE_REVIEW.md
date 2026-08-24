# AutoJump 自动化架构复盘与重构方案

日期：2026-08-24

## 结论

当前方向存在结构性问题。系统把“无障碍节点点击”和“用户触摸录制回放”合并成同一种 `CLICK` 动作，并把录制坐标当作节点选择的辅助信息。这样做会导致：

1. 录制结果不是用户的真实触摸。当前代码从 `TYPE_VIEW_CLICKED` 的节点边界计算中心点，既没有记录按下点，也没有记录触摸时长和移动轨迹。
2. 回放不是忠实回放。只要找到候选节点，代码会用候选节点中心覆盖录制坐标。
3. WebView、Canvas 或自绘按钮的节点语义不可靠，却仍然控制坐标动作何时执行以及落在哪里。
4. `dispatchGesture` 的完成回调只证明系统派发完手势，不能证明目标应用完成了打卡。
5. ADB 的成功不能直接转化为普通 APK 的能力。Shell 输入注入依赖普通应用拿不到的系统级权限。

因此，新架构必须把“动作怎么找到目标”“怎么产生输入”“怎么确认业务成功”拆开。

## 现状证据

### 代码证据

- `RuleAction.CLICK` 同时保存节点选择器和相对坐标，没有表达两种动作不同的执行语义。
- `captureRecordedClick()` 读取无障碍节点的 `boundsInScreen`，保存的是矩形中心，而非原始 `MotionEvent`。
- `ClickTargetResolver` 找到文本、ID 或描述候选后，才决定执行手势。
- `clickCoordinate()` 在存在候选时使用候选中心覆盖录制坐标。
- 手势回调 `onCompleted()` 后立即推进动作；日志已经改为“已派发”，但会话仍缺少验证阶段。

### 真机证据

- 用户手动点击飞书“打卡”成功。
- 同位置的瞬时 ADB tap 和无障碍节点 `ACTION_CLICK` 没有触发业务结果。
- ADB 持续约 160ms 的触摸成功触发“打卡成功”。
- AutoJump 的零长度 160ms 手势被系统接受，但没有产生新的打卡记录。

这组结果说明坐标本身不是唯一变量。目标页面可能依赖完整的触摸事件序列、持续时间和微小移动。继续只调整节点匹配或固定手势参数，无法建立可靠模型。

## 平台能力边界

### 普通 APK 可以做的事

- 通过无障碍服务读取允许暴露的节点、执行 `ACTION_CLICK`、用 `dispatchGesture` 派发屏幕手势。
- Android 11（API 30）起，在声明能力后通过无障碍服务截图；Android 14（API 34）起可以截取指定窗口，避免无障碍悬浮层遮挡目标窗口。
- 通过可信的 `TYPE_ACCESSIBILITY_OVERLAY` 显示录制控制层。
- 唤醒屏幕并请求系统解除键盘锁；非安全锁或已受信任状态可能直接解除。

### 普通 APK 不能承诺的事

- 不能获得 Shell/系统签名级输入注入能力，因此不能把 ADB `input` 当作正式包默认执行后端。
- 不能绕过 PIN、图案、密码或生物识别。安全锁屏必须由用户通过系统界面认证。
- 无法从 `dispatchGesture` 回调得知“打卡成功”；只能得知系统接受、完成或取消了手势。
- 截图会受安全窗口限制；MediaProjection 需要用户同意，也只是捕获画面，不提供触摸注入。

## 技术路线比较

| 路线 | 优点 | 主要问题 | 定位 |
| --- | --- | --- | --- |
| 无障碍节点 `ACTION_CLICK` | 语义清晰、适配分辨率 | WebView/自绘控件可能返回成功但无业务响应；重复文本容易误选 | 仅用于稳定原生控件 |
| 固定坐标 + 固定时长 | 简单、普通 APK 可执行 | 布局、方向、系统栏变化会漂移；未复现真实触摸 | 不作为最终录制模型 |
| 完整触摸轨迹录制回放 | 最接近用户成功操作；保留按下、移动、时长、抬起 | 仍依赖 `dispatchGesture`；需实现全屏录制层和坐标变换 | 推荐主执行后端 |
| 截图模板/OCR 定位后触摸 | 可处理按钮位置变化和 WebView | 截图权限、性能、相似按钮误判；OCR 文本不等于可点击区域 | 推荐增强层，不单独执行 |
| ADB/UI Automator | 已证明可触发；适合稳定回归 | 需要电脑/测试运行器，不是普通 APK 运行能力 | 开发与验收工具 |
| Shizuku/root/系统应用 | 可提供更强输入注入 | 部署复杂、权限风险高、用户门槛高 | 可选高级后端，不能成为默认方案 |
| 目标应用业务接口 | 最稳定且容易验证 | 需要目标应用官方授权；不可假设飞书提供可用打卡接口 | 有正式授权时优先 |

### 同类工具的设计启示

- Automate 将“前台应用确认”“等待页面绘制”和“触摸手势”作为独立步骤，并明确把模拟触摸视为找不到其他手段时的最后方案；它的悬浮录制器记录屏幕位置，而不是假装节点结果就是触摸录制。
- MacroDroid 把文本、View ID、X/Y 和“在应用中识别”作为并列的 UI Interaction 配置，并直接提醒坐标/界面自动化会因应用 UI 更新而脆弱。
- AutoInput 同样把 UI Query、目标包等待和坐标动作分开。
- Shizuku 的定位就是让应用通过由 ADB/root 启动的独立服务获得更高能力，这印证了特权输入必须是单独部署和授权的后端，不能混入普通 APK 能力描述。

这些工具的共同点不是某一种点击方法万能，而是把应用状态、目标定位、输入方式和失败路径显式拆开。AutoJump 当前最需要补的正是这层边界。

## 推荐架构

采用“真实触摸脚本 + 视觉锚点 + 业务验证”的分层架构。节点自动化保留，但不再干预录制触摸动作。

```text
规则触发
  -> 解锁门禁
  -> Deeplink 导航
  -> 目标应用与窗口稳定检查
  -> 动作定位器（原始坐标 / 视觉锚点 / 原生节点）
  -> 输入后端（无障碍轨迹回放 / 原生节点 / 可选特权输入）
  -> 结果验证器（截图变化 / 成功文案 / 记录页状态）
  -> VERIFIED 或 UNVERIFIED/FAILED
```

### 1. 动作模型分离

不能继续用一个带大量可选字段的 `CLICK` 表示所有情况。建议 schema v3 使用独立动作类型：

- `DEEPLINK`
- `WAIT`
- `RECORDED_TOUCH`
- `ACCESSIBILITY_NODE`
- `VERIFY_VISUAL`
- `VERIFY_NODE`

`RECORDED_TOUCH` 的核心数据：

- 目标包名及可选目标 Activity/窗口信息；
- 录制时应用版本、屏幕方向、显示尺寸、密度、系统栏和应用可用区域；
- 从触摸 `DOWN` 到 `UP/CANCEL` 的归一化轨迹点 `{x, y, elapsedMs}`；
- 录制前目标区域截图及裁剪锚点；
- 回放前延迟、窗口稳定时长、超时和重试策略；
- 验证规则。

节点的 ID、文本和描述可以作为诊断元数据或视觉搜索提示，但不能覆盖轨迹坐标。

### 2. 真正的录制流程

现有“开始录制”小悬浮窗可以保留，但点击开始后的机制需要重做：

1. 确认当前目标包和窗口稳定。
2. 在覆盖目标页面前截取目标窗口，用于保存视觉锚点。
3. 显示一次性的全屏透明 `TYPE_ACCESSIBILITY_OVERLAY` 触摸捕获层。
4. 捕获用户下一次从 `ACTION_DOWN` 到 `ACTION_UP` 的全部有效轨迹和时间。
5. 隐藏捕获层，立即按同一轨迹向目标窗口回放，让这次录制同时完成用户原本的点击。
6. 只有用户确认目标应用确实产生预期结果后，才保存动作；否则允许重录。

全屏捕获层会消费原触摸，这是刻意设计：它先记录，再隐藏并回放。这样才能得到真实手指落点、持续时间和微小移动，而不是节点中心。

Android 14 可评估 `onMotionEvent`，但该 API 同样会截获所请求来源的事件；为了覆盖项目 minSdk 21，主方案仍以无障碍触摸覆盖层为统一抽象。

### 3. 回放流程

1. Deeplink 打开后，不立即依赖某个文本节点。
2. 等待目标包连续稳定一段时间，例如 500ms，并确认方向、显示区域可换算。
3. 优先在录制位置附近匹配视觉锚点；匹配可靠时只计算平移修正，不改变轨迹形状和时长。
4. 无视觉能力或匹配不可靠时，在设备配置兼容的前提下使用原始归一化轨迹。
5. 通过 `dispatchGesture` 回放完整 Path 和时间序列。
6. 系统回调完成后进入 `VERIFYING`，而不是直接视为动作成功。

节点动作走完全独立的路径：只有用户明确创建 `ACCESSIBILITY_NODE` 动作时，才查找节点并执行 `ACTION_CLICK`。

### 4. 视觉定位

视觉层用于“微调已录制位置”，不把 OCR 文本直接等同于按钮：

- 首选录制时按钮周围的局部图像模板和几何邻域；
- OCR 可识别“打卡”“打卡成功”等文本并返回边界，但必须结合记录位置、大小、颜色/模板和页面区域；
- “底部适应视线”一类重复文本不能单凭文字选择；
- 匹配置信度低时停止并提示重录，避免误点。

API 30+ 优先使用无障碍截图。API 21-29 若确实需要视觉能力，只能选择用户授权的 MediaProjection 或降级为设备配置匹配的原始轨迹；MediaProjection 不参与点击注入。

### 5. 结果验证

状态机需要明确区分输入传输和业务结果：

```text
ENQUEUED -> NAVIGATING -> READY -> REPLAYING -> DISPATCHED
         -> VERIFYING -> VERIFIED
                      -> UNVERIFIED / FAILED
```

飞书打卡至少支持以下一种验证方式：

- 识别“打卡成功”提示或成功状态截图；
- 打开考勤记录页，确认出现本次时间附近的新记录；
- 用户录制一个“成功画面”作为视觉验证锚点。

验证超时不能显示“成功”，应显示“已点击，但未确认打卡结果”。可配置一次完整重试：重新打开 Deeplink、等待稳定、重新回放、重新验证；不能在未知页面连续盲点。

### 6. 解锁

- 屏幕未设置安全锁或设备处于可信解锁状态：请求系统解锁后自动继续。
- PIN、图案、密码或生物识别生效：展示系统认证，用户完成后恢复同一会话。
- 不录制、不保存、不回放锁屏凭据。
- 解锁后必须重新导航目标 Deeplink，不能假设锁屏前页面仍保持相同状态。

### 7. 特权后端边界

ADB、UI Automator、Shizuku/root 必须与正式普通 APK 后端分开：

- ADB/UI Automator 用于开发机自动回归和采集证据；
- Shizuku/root 可作为用户显式启用的高级执行后端；
- 普通安装默认永远使用无障碍轨迹回放；
- 日志必须记录本次实际使用的后端，避免再次把 ADB 成功误认为 APK 成功。

## 非功能要求

### 可靠性

- 相同设备配置、目标应用版本和页面状态下，录制轨迹回放成功率目标不低于 95%。
- 每次 `VERIFIED` 必须有业务验证器证据，不能来自派发回调。
- 旋转、分屏、分辨率或应用可用区域无法安全换算时拒绝执行并提示重录。

### 安全与隐私

- 只对规则绑定的精确包名派发动作。
- 不在系统 UI、权限页、锁屏、输入法或密码控件执行和录制。
- 截图默认只保存目标附近裁剪图；设置明确的保留期限并支持一键删除。
- 日志不保存完整 Deeplink 查询参数、完整页面文本、密码或整屏截图。

### 可观测性

每次执行至少记录：规则、动作 ID、目标包、设备配置是否匹配、定位方式、输入后端、派发状态、验证方式和最终结果。调试导出时对敏感字段脱敏。

### 兼容性

- minSdk 21 保留基础规则能力；`TYPE_ACCESSIBILITY_OVERLAY` 从 API 22 可用，因此 API 21 不支持新的悬浮录制。
- API 24+ 才可通过 `dispatchGesture` 自动回放；API 21-23 应明确标记不支持自动触摸，录制入口也不应制造可自动回放的承诺。
- API 30+ 启用无障碍截图增强；API 34+ 优先指定窗口截图。

## 分阶段实施

### 阶段 0：冻结错误抽象

- 不再给当前 `CLICK` 增加新的候选匹配和固定手势特例。
- 保留 4px 手势为实验分支证据，不作为正式修复提交。
- 增加 schema v3 迁移设计，旧规则仍可读取。

### 阶段 1：触摸脚本最小闭环

- 新建 `RECORDED_TOUCH` 模型和持久化。
- 实现全屏一次性触摸捕获层，记录真实轨迹与时长。
- 录制结束立即原样回放并由用户确认。
- 回放时完全忽略节点候选，不覆盖录制坐标。

验收：在小米 14 Pro 上录制用户已知可成功的飞书打卡触摸；重新打开 Deeplink 后由 AutoJump 回放，飞书实际新增打卡结果。

### 阶段 2：业务验证和安全重试

- 增加 `VERIFYING/VERIFIED/UNVERIFIED`。
- 先支持用户录制成功画面和 OCR 成功文案。
- 仅允许一次“重新导航后完整重试”。

验收：没有新增打卡记录时绝不显示成功；新增记录时显示验证证据和时间。

### 阶段 3：视觉锚点适配

- API 30+ 采集局部截图模板。
- 在录制点邻域做模板定位并平移轨迹。
- 处理系统栏、横竖屏和应用可用区域变换。

验收：按钮发生小范围纵向变化时仍能可靠命中；出现多个“打卡”文本时不会选择底部导航项。

### 阶段 4：可选高级后端

- 仅在普通无障碍轨迹被特定 ROM/应用拒绝、且用户明确需要时评估 Shizuku。
- UI Automator 保持为测试工程，不嵌入正式运行链路。

## 测试矩阵

| 场景 | 必须验证 |
| --- | --- |
| 同设备、同方向、同版本 | 真实打卡记录新增 |
| 按钮小幅移动 | 视觉锚点修正后成功 |
| 出现底部同名入口 | 不误点，必要时停止 |
| 页面未加载完成 | 等待稳定，超时后不盲点 |
| 手势已派发但业务无变化 | 标记 UNVERIFIED，并按策略重试一次 |
| 手机安全锁屏 | 用户系统认证后重新导航并继续 |
| 分辨率/方向不兼容 | 拒绝执行并提示重录 |
| 无障碍服务被系统终止 | 会话可恢复，不重复无限点击 |

## 参考资料

- Android AccessibilityService：<https://developer.android.com/reference/android/accessibilityservice/AccessibilityService>
- Android GestureDescription：<https://developer.android.com/reference/android/accessibilityservice/GestureDescription>
- Android AccessibilityServiceInfo：<https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo>
- Android WindowManager.LayoutParams：<https://developer.android.com/reference/android/view/WindowManager.LayoutParams>
- Android MediaProjection：<https://developer.android.com/media/grow/media-projection>
- Android UI Automator：<https://developer.android.com/training/testing/other-components/ui-automator>
- ML Kit Text Recognition v2：<https://developers.google.com/ml-kit/vision/text-recognition/v2>
- Google Play Accessibility API policy：<https://support.google.com/googleplay/android-developer/answer/17190352>
- Automate Interact touch：<https://llamalab.com/automate/doc/block/interact_touch.html>
- MacroDroid UI Interaction：<https://www.macrodroidforum.com/wiki/index.php/Action%3A_UI_Interaction>
- Shizuku Introduction：<https://shizuku.rikka.app/introduction/>
