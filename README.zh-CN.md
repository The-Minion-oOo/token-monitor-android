# Token Monitor Android

[English](README.md) · [下载 APK](https://github.com/The-Minion-oOo/token-monitor-android/releases/latest) ·
[连接指南](docs/PAIRING.md) · [语言说明](docs/LANGUAGES.md)

把桌面 Token Monitor 的用量看板带到手机上：Token 总量、费用估算、账户额度、
工具与模型、设备、项目、会话和历史趋势。桌面负责采集，Android 只读展示，
无需在手机上安装另一套采集器。

## r2 候选版的新功能

当前公开下载仍是 **v0.79.0 r1**。以下功能已加入 **v0.79.0 r2 候选版**，
正式发布后才会出现在下载的 APK 中。

- **12 种界面语言：** 简体中文、繁體中文、英语、韩语、日语、巴西葡萄牙语、
  西班牙语、印地语、古吉拉特语、孟加拉语、泰米尔语和泰卢固语。小组件也使用所选语言。
- **付款记录更清楚：** 区分订阅与充值，查看充值明细、续订日期、到期日期和备注。
- **额度说明更完整：** 显示重置次数、有效期、重置资格和服务商报告的 Token 明细。
- **只看需要的内容：** 置顶、排序或隐藏工具，选择要显示的额度窗口和服务商信息。
- **以熟悉的货币查看费用：** 支持 USD、CNY、TWD、HKD、INR、EUR、JPY、KRW、BRL，
  使用保存的自动汇率或手动汇率。服务商余额和付款记录保留原始币种。
- **可选实时速率：** 查看近期输出速度或 Token 消耗速率，与会话平均速度分开显示，
  不提高看板更新频率。
- **先预览再分享诊断：** 报告不包含地址、密钥、账户身份、对话文字或缓存响应。

## 开始使用

1. 在 Android 8 或更新版本上安装本仓库 Releases 提供的 APK。
2. 在桌面 Token Monitor 中打开 **Settings → Multi-device Sync**，启用 Hub。
3. 推荐在桌面和手机上安装 Tailscale，加入同一个 tailnet。
4. 在 Android 中输入桌面的 Hub 地址与共享密钥并连接。

也可选择家庭 Wi-Fi，或单独启用已有的公网 HTTPS Hub。公网连接默认关闭，
要求公共 DNS 域名和受系统信任的 HTTPS 证书。手机不会替你开放桌面端口。

## 不打开应用，也能查看

**Usage** 小组件随尺寸调整内容。**Pages** 小组件在同一张卡片中切换总览、
额度、明细和活跃情况四页。

Refresh 进行一次更新；Live 最多运行一小时，可随时停止。可选的后台刷新
每 30 或 60 分钟检查一次，默认关闭并默认仅使用 Wi-Fi。Android 可能延迟休眠时的更新。

这是独立开发的 Android 伴侣应用。登录服务商、采集、编辑价格/订阅和管理 Hub
仍在桌面完成。手机不会控制电脑，也不会读取对话正文。

[桌面功能对照](docs/DESKTOP_PARITY.md) · [安装与更新](docs/INSTALL.md) ·
[小组件](docs/WIDGETS.md) · [验证记录](docs/VALIDATION.md) · [安全与隐私](docs/SECURITY.md)

r2 尚未在实体手机上测试；该项记录在
[#94](https://github.com/The-Minion-oOo/token-monitor-android/issues/94)，
与自动化和模拟器发布检查分开跟踪。
