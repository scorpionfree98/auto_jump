# AutoJumpApp · 一键跳转

点开图标自动跳转到指定 deeplink 的安卓 App，支持**条件规则引擎**、长按快捷菜单、桌面独立快捷、配置增删改、本地持久化。适配 Android 7.1 ~ 14。

## 核心功能

### 1. 条件规则引擎 ⭐ 新功能

**解决痛点**：HyperOS 自动任务只能"启动应用"，无法直接打开链接或传参数。

**方案**：App 内置规则引擎，启动时根据当前状态（时间、WiFi、蓝牙、位置、充电）自动匹配规则，执行对应跳转。

支持的条件：
- **时间段**：周一~五 08:00-09:00（无需权限）✨ 支持星期几选择器
- **WiFi**：连接到指定 WiFi SSID（需位置权限，Android 10+）
- **蓝牙**：已配对指定蓝牙设备（需蓝牙权限）
- **充电状态**：充电中/未充电（无需权限）
- **地理位置**：在目标位置 X 米范围内（需后台定位权限，敏感）✨ 支持一键获取当前位置

一条规则支持：
- 多个条件 AND 组合
- 多个动作（deeplink）顺序执行
- 优先级控制
- **✨ 规则导入导出**（JSON 格式，支持备份/分享/多设备同步）

**典型场景**：
- 工作日早上 8 点 + 连接公司 WiFi → 自动跳飞书工作台 + 钉钉打卡
- 连接 CarPlay + 下班时间 → 自动打开高德导航回家
- 健身房位置 500 米内 + 晚上 7-10 点 → 自动打开音乐 App

详见 [规则引擎说明文档](RULE_ENGINE.md) 和 [更新日志](CHANGELOG.md)

### 2. 桌面快捷方式

配置页里为每个跳转指令「钉到桌面」，生成独立图标。适合不需要条件判断的固定跳转。

### 3. 长按快捷菜单

长按 App 图标弹出快捷菜单（Android 7.1+），快速进入设置或已配置的跳转。

## 使用方式

### 方式 A：规则引擎（推荐，配合 HyperOS 自动任务）

1. **配置规则**：长按图标 → 设置 → 新增规则 → 添加条件和动作
2. **授予权限**：菜单 → 权限设置 → 根据条件类型授权
3. **HyperOS 自动任务**：
   ```
   触发条件: 定时 - 每 15 分钟
   执行动作: 启动应用 - 选择本 App
   ```
4. **测试**：规则列表页 → 测试运行

### 方式 B：桌面快捷方式

长按图标 → 设置 → 新增配置 → 输入名称和 deeplink → 钉到桌面

### 方式 C：直接点图标

点图标执行默认跳转（兼容旧版本）。如果配置了规则，优先执行规则匹配。

## 编译 APK（Docker 方案，无需 Android Studio）

```bash
cd AutoJumpApp
bash docker/run.sh --name "飞书直达" --output "feishu.apk"
```

首次会构建镜像（约 15 分钟），之后有缓存很快。

可用参数：
| 参数 | 说明 | 示例 |
|------|------|------|
| `--name`    | 程序名称（桌面显示名） | `--name "飞书直达"` |
| `--link`    | 默认跳转的 deeplink | `--link "example://page/path"` |
| `--package` | applicationId（包名，决定能否多开并存） | `--package "com.autojump.feishu"` |
| `--output`  | 输出 APK 文件名 | `--output "feishu.apk"` |

**批量生产**示例：
```bash
bash docker/run.sh --name "飞书直达" --package "com.autojump.feishu" \
  --link "example://page/path" --output "feishu.apk"

bash docker/run.sh --name "钉钉直达" --package "com.autojump.dingtalk" \
  --link "dingtalk://..." --output "dingtalk.apk"
```

不同包名可以并存安装，HyperOS 自动任务能各自选中。

## 技术栈

- Kotlin/Java
- AndroidX + Material Design
- Gson（JSON 序列化）
- RecyclerView（规则列表）
- ShortcutManager（快捷方式）

## 权限说明

| 权限 | 用途 | 必需性 |
|------|------|--------|
| `INSTALL_SHORTCUT` | 创建桌面快捷方式 | 可选（不用快捷方式可不给） |
| `ACCESS_WIFI_STATE` | WiFi 条件判断 | 可选（用 WiFi 条件时需要） |
| `ACCESS_FINE_LOCATION` | WiFi SSID 读取（Android 10+）、地理位置条件 | 可选 |
| `ACCESS_BACKGROUND_LOCATION` | 后台地理位置条件 | 可选（敏感，仅必要时用） |
| `BLUETOOTH_CONNECT` | 蓝牙设备条件判断 | 可选（用蓝牙条件时需要） |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | 规则导入导出（JSON 文件） | 可选（需要备份规则时用） |

## FAQ

### Q1：能否在 App 内自动生成新 APK 并改名字？

**不能**。手机上的 App 无法自我编译生成 APK，也无法修改自己的桌面名称（名称写死在 manifest 里，签名后不可改）。

**正确做法**：在电脑上用 Docker 脚本批量生成多个不同名字/不同包名的 APK。

### Q2：为什么 HyperOS 自动任务不能直接打开链接？

澎湃 OS 的自动任务比老 MIUI 更严格，只有"启动应用"这一个动作，无法"打开链接"或传参数。

**解决方案**：把条件判断逻辑放进 App 内部（规则引擎），HyperOS 只负责定时启动 App。

### Q3：桌面快捷方式为什么不能被自动任务识别？

自动任务只识别"应用"，不识别"快捷方式"。所以必须用规则引擎方案。

### Q4：每 15 分钟启动一次会不会很耗电？

测算约 1-2% 每天。App 启动 1-2 秒就退出，几乎无影响。只要不用地理位置条件，几乎不增加额外耗电。

### Q5：蓝牙条件是"已连接"还是"已配对"？

当前实现是"已配对"（真正的已连接需要监听广播比较复杂）。对于 CarPlay、耳机等场景，"已配对"通常等价于"正在使用"。

### Q6：位置条件准确吗？

使用的是 `getLastKnownLocation()`（上次定位缓存）。如果设备长时间未定位，可能拿到旧位置。建议半径设大一点（500-1000 米）。

## 目录结构

```
AutoJumpApp/
├── README.md                  总说明
├── RULE_ENGINE.md             规则引擎详细文档
├── build.gradle / settings.gradle / gradle.properties
├── app/
│   ├── src/main/
│   │   ├── java/com/autojump/app/
│   │   │   ├── MainActivity.java              主入口（执行规则匹配）
│   │   │   ├── RuleListActivity.java          规则列表页
│   │   │   ├── RuleEditActivity.java          规则编辑页
│   │   │   ├── PermissionGuideActivity.java   权限引导页
│   │   │   ├── SettingsActivity.java          旧配置页（兼容）
│   │   │   ├── bean/
│   │   │   │   ├── Rule.java                  规则数据结构
│   │   │   │   └── ConfigBean.java            快捷配置（兼容）
│   │   │   ├── condition/
│   │   │   │   ├── TimeCondition.java         时间段条件
│   │   │   │   ├── WiFiCondition.java         WiFi 条件
│   │   │   │   ├── BluetoothCondition.java    蓝牙条件
│   │   │   │   ├── ChargingCondition.java     充电条件
│   │   │   │   └── LocationCondition.java     位置条件
│   │   │   ├── util/
│   │   │   │   ├── RuleEngine.java            规则匹配引擎
│   │   │   │   ├── SPUtil.java                持久化工具
│   │   │   │   └── ShortcutUtil.java          快捷方式工具
│   │   │   └── adapter/
│   │   │       ├── RuleAdapter.java           规则列表适配器
│   │   │       └── ConfigAdapter.java         快捷配置适配器
│   │   └── res/                                布局、图标、字符串资源
└── docker/
    ├── Dockerfile             编译环境镜像
    ├── run.sh                 一键：建镜像 + 编译
    └── build_apk.sh           参数化改源码 + 编译 + 自动还原
```

## License

MIT

## 贡献

欢迎提 Issue 和 PR。
