# 手机端模板 UI 替换设计

日期：2026-06-19

## 目标

将 `手机模版` 中的手机端 Compose 界面视觉迁移到现有 `phone/app`，让发送页面从工程化表单变成更完整的产品界面。

本次只迁移表现层。保留现有手机端的蓝牙权限、蓝牙开关检测、已配对设备读取、多设备选择、文件多选、系统分享入口、发送队列、取消、进度和错误处理逻辑。

不引入模板工程里的 Gemini、Secrets、Room、Retrofit、KSP、Robolectric/Robolectric screenshot 配置等无关依赖。不改变传输协议、ViewModel 状态机或蓝牙发送实现。

## 视觉来源

视觉参考来自 `手机模版/app/src/main/java/com/example/MainActivity.kt`：

- 大标题 `Watch Transfer`。
- `目标设备` 区块，使用圆形手表图标、主副文案和右箭头。
- `传输队列` 区块，标题右侧有添加文件入口。
- 空态使用上传文件图标、说明文案和可点击添加区域。
- 文件列表使用文件图标、文件名、状态文案、移除按钮和发送进度条。
- 满足发送条件时显示居中的 `发送到手表` 浮动按钮。

模板中的本地假状态和模拟进度不迁移。现有 `PhoneSenderUiState` 继续作为真实 UI 输入。

## 屏幕结构

`PhoneSenderScreen` 保持单屏 Compose 页面，入口参数不变，方便 `MainActivity` 和现有测试继续复用。

页面布局：

```text
Scaffold
  LargeTopAppBar: Watch Transfer
  LazyColumn
    状态阻断卡片（仅 MissingPermission / BluetoothOff / NoBondedWatch）
    目标设备标题
    DeviceSelectorCard
    传输队列标题 + 添加文件 TextButton
    EmptyFileState 或 FileItemRow 列表
    发送状态/整体进度区
  FAB: 发送到手表（满足 canSend 且未发送中）
  取消按钮/发送中状态入口
  AlertDialog: 多设备选择
```

阻断状态保持现有语义：

- 缺少权限：展示授权按钮，触发 `onRequestPermission`。
- 蓝牙关闭：提示到系统设置开启蓝牙。
- 无配对手表：提示先完成系统蓝牙配对。

Ready 状态下展示完整发送界面。

## 交互规则

设备选择：

- 点击设备卡片时，如果只有一个设备，直接触发 `onDeviceSelected`。
- 如果有多个设备，弹出 `AlertDialog` 列表让用户选择。
- 卡片选中后显示设备名称和“准备就绪”类副文案。

文件选择：

- 标题右侧 `添加文件` 和空态区域都触发 `onPickFiles`。
- 有文件时展示队列列表，支持多文件。
- 未发送时显示移除按钮；发送中隐藏或禁用移除，避免队列状态被打乱。

发送与取消：

- `state.canSend && !state.isSending` 时显示模板风格 `ExtendedFloatingActionButton`。
- 发送中隐藏发送 FAB，显示当前状态和进度。
- 发送中保留取消入口，触发 `onCancel`。

进度：

- 每个文件行使用 `PhoneFileUiItem.status` 展示等待、发送中、成功或失败。
- `state.currentProgress` 展示当前文件进度。
- `state.totalProgress` 展示整队列进度。
- 当进度为 0 且未发送时，进度条可以隐藏，减少视觉噪音。

## 实现边界

主要修改：

- `phone/app/src/main/java/com/example/watchtransfer/phone/ui/PhoneSenderScreen.kt`
  - 用模板视觉重写屏幕内部布局。
  - 新增私有 composable：`DeviceSelectorCard`、`EmptyFileState`、`FileItemRow`、`BlockingStatusCard`、`TransferProgressPanel`。
  - 复用现有 `PhoneSenderUiState`、`DeviceUiItem`、`PhoneFileUiItem` 数据结构。

可能需要修改：

- `phone/app/build.gradle.kts`
  - 如果当前模块没有 Material Icons 依赖，添加 `androidx.compose.material:material-icons-extended` 或等价依赖，用于 `Watch`、`UploadFile`、`InsertDriveFile`、`Close`、`Add`、`Send`、`ChevronRight` 图标。

不修改：

- `MainActivity` 的权限、蓝牙和文件选择 wiring。
- `SendQueueViewModel`。
- `WatchTransferSender`。
- `shared/transfer-protocol`。
- `watch/app`。

## 可访问性与适配

- 按钮和图标提供可理解的 `contentDescription`，装饰性图标使用 `null`。
- 文件名保持单行省略，避免长文件名撑破布局。
- 页面使用 `LazyColumn`，小屏可滚动。
- 主操作按钮保留足够底部 padding，避免遮挡队列最后一项。
- 使用 `MaterialTheme.colorScheme` 和 `MaterialTheme.typography`，跟随现有主题和系统深色模式。

## 测试与验证

自动验证：

```powershell
.\gradlew.bat :phone:app:testDebugUnitTest
.\gradlew.bat :phone:app:assembleDebug
```

如果增加图标依赖，再运行：

```powershell
.\gradlew.bat :phone:app:compileDebugKotlin
```

手动验证：

- 缺权限、蓝牙关闭、无配对设备三个阻断状态文案和按钮正确。
- 单设备时点击设备卡片能选择设备。
- 多设备时弹出选择对话框。
- 空态点击能打开多文件选择器。
- 多个文件加入后列表展示稳定，长文件名不会挤坏布局。
- 可发送时显示 `发送到手表` 主按钮。
- 发送中显示进度和取消入口，不能随意移除文件。
- 发送结束后成功/失败状态仍可读。

## 后续扩展

这次不做完整产品化，但后续可以继续：

- 增加手机端专属主题 token。
- 添加最近发送历史。
- 增加失败文件单项重试。
- 优化真机截图回归测试。
