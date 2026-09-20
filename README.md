# ZEEHO Ride Speed Restore

LSPosed 模块，用来恢复 ZEEHO（`com.cfmoto`）新版 App 中被隐藏的骑行速度字段。

推荐的 GitHub 仓库名：`zeeho-ride-speed-restore`

## 项目状态

本项目针对 ZEEHO 3.0.4（versionCode 399）开发，基于 2.6.14（versionCode 376）与新版资源对比定位。当前版本为 1.1.0，已在实际设备上验证以下页面可以恢复速度显示：

- “历史轨迹”列表中的每条骑行速度；
- 轨迹详情中的统计速度和回放速度；
- “我的骑行 → 当日数据”中的最高速度。

不同车型、账号、地区和后续 App 版本的接口或页面可能不同，不能据此保证所有环境都能显示。

## 工作范围

模块只处理骑行记录相关 Activity：

- `com.cfmoto.ui.moto.HistoryTravelActivity`：恢复列表项 `tv_travel_speed`；
- `com.cfmoto.ui.moto.TravelDetailActivity`：恢复 `tv_speed`、`tv_speed_playing` 及详情统计卡；
- `com.cfmoto.ui.moto.myrideinfo.MyRideInfoActivity`：恢复“当日数据”中的 `tv_speed`。

模块不处理导航实时速度，也不读取或伪造 GPS 速度。它通过稳定的资源 ID 名称识别控件，并兼容 RecyclerView 异步创建的记录项，因此不依赖 ZEEHO 被 Ijiami 加固的业务类名。

## 为什么需要这个模块

2.6.14 和 3.0.4 的骑行记录布局仍然保留速度控件，新版主要是在运行时将控件或其父容器设为不可见，部分版本还可能不再给字段赋值。本模块只恢复“数据已经存在但界面隐藏”的情况；如果服务端响应没有速度值，模块不会凭空生成数据。

## 安装

1. 在 LSPosed 中安装发布包。
2. 将作用域限定为 ZEEHO：`com.cfmoto`。
3. 启用模块后强制停止并重新打开 ZEEHO。
4. 进入“我的骑行”或“历史轨迹”查看结果。

发布包使用本地调试密钥签名，适合个人设备测试。公开分发前请使用自己的密钥重新构建和签名。

## 从源码构建

环境要求：JDK 17、Android SDK 35、Gradle 8.7、Android Gradle Plugin 8.6.1。

```powershell
$env:JAVA_HOME = "<你的 JDK 17 路径>"
$env:ANDROID_SDK_ROOT = "<你的 Android SDK 路径>"
gradle assembleRelease
```

源码使用传统 Xposed API（`de.robv.android.xposed:api:82`），生成的 APK 通过 `assets/xposed_init` 注册 `io.github.codex.zeehospeed.MainHook`。

## 隐私与安全

- 模块没有网络权限，不上传骑行记录、车辆信息、位置或账号数据；
- 模块不修改 ZEEHO APK，不绕过账号权限，也不改变车辆控制逻辑；
- 速度数据只用于界面显示，不能作为仪表或安全决策的替代品；
- 请勿在骑行过程中操作手机或依赖本模块读数；
- LSPosed、Root、加固 App 和第三方模块存在兼容性风险，使用者应自行备份并承担风险。

## 非官方声明

本项目与 ZEEHO、CFMOTO 及其关联公司没有隶属、授权、赞助或合作关系。ZEEHO、CFMOTO 及相关图标、名称和产品资料归其各自权利人所有。本项目仅用于个人设备上的兼容性研究和界面恢复，不提供任何官方支持，也不保证持续兼容。

使用者应确保对目标设备、系统和 App 具有合法使用权，并自行遵守所在地法律、软件许可协议、服务条款和厂商保修政策。作者不对因安装、使用、修改或分发本项目造成的数据丢失、设备异常、账号限制、保修影响或其他直接、间接损失承担责任。

## 许可证

本项目原创源码按 [MIT License](LICENSE) 发布。许可证只适用于本仓库中的原创源码和文档，不适用于 ZEEHO/CFMOTO 的 APK、商标、图标、地图素材、接口数据或其他第三方依赖；这些内容仍受各自权利人和适用许可条款约束。
