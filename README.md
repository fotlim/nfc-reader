# NFC 读卡器（NFC Reader）

一个完全免费、无内购、无广告的 Android NFC 读卡工具，Material 3 设计语言，可编译成 APK 单独安装，也可以作为预装应用打进自定义 ROM。

> 写它的原因很简单：市面上不少 NFC 读卡 App 把「读 Mifare Classic 扇区」「读 NDEF 全文」「导出数据」这些基本功做成付费点，所以我做了一个全功能的。

---

## 功能

- **UID**：正序 / 反序 / 长度
- **技术协议**：列出卡片支持的全部 NFC 技术（NfcA / NfcB / NfcF / NfcV / IsoDep / MifareClassic / MifareUltralight / Ndef / NdefFormatable）
- **NfcA**：ATQA、SAK
- **NfcB**：Application Data、Protocol Info
- **NfcF**：厂商码、系统码
- **NfcV**：DSF ID、响应标志
- **IsoDep**：历史字节（Historical Bytes）、高层响应、扩展 APDU、超时；内置 `SELECT MF`（`00A4040000`）与 `GET UID`（`00CA000000`）两条常用指令
- **Mifare Classic**：6 组常用 KeyA 逐扇区认证 + 逐块读（上限 512 块），常见出厂密钥（`FFFFFFFFFFFF`、`A0A1A2A3A4A5`、`D3F7D3F7D3F7`、`000000000000` 等）自动尝试
- **Mifare Ultralight**：逐页读取（0~43，含序列号与 BCC0）
- **NDEF 全文解析**：TNF、文本（含语言码）、URI、MIME、外部类型
- **UID 一键复制 / 分享 / 清空**
- **读卡后弹出「选择操作」**：可把卡片交给系统里其它能处理 NFC 的应用，也可选择「查看详情」「复制 UID」

## 界面

Material 3（Material You）：

- 顶部 App Bar：标题 + 副标题「免费 · 无内购 · 全功能」
- 状态胶囊：实时显示 NFC 是否开启、是否就绪
- UID 卡 + 详情卡：24dp 圆角、1dp elevation
- 底部三按钮：填充式「复制」、色调式「分享」「清空」，带水波纹
- 自动跟随系统深色 / 浅色主题
- 状态栏、导航栏安全区自适应

## 构建

不需要 Gradle，直接调 AOSP 里的工具链（本工程在一个完整的 Android 源码树里编译）。

依赖：

- `aapt2`、`d8`、`zipalign`、`apksigner`
- JDK 21（d8 需要 JDK 21，JDK 17 会报 `UnsupportedClassVersionError`）
- `android.jar` 用 `prebuilts/sdk/current/public/android.jar`

```bash
# 在 Android 源码树根目录执行
export JAVA_HOME=$PWD/prebuilts/jdk/jdk21/linux-x86
export PATH=$JAVA_HOME/bin:$PATH

bash buildapk.sh
# 产物：/root/nfcout/nfc-reader.apk
```

`buildapk.sh` 也可以用 `SRC` / `OUT` / `AJAR` 环境变量指向自己的路径。

> 注意：签名用的 keystore 必须固定，否则重装会报
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`。
> 脚本默认用 `/root/nfckey.jks`（storepass / keypass 都是 `android`，alias `testkey`）。

## 作为预装应用打进 ROM

`rom/Android.mk` 是一个 `BUILD_PREBUILT` 模块，把编译好的 APK 预置进系统：

```makefile
LOCAL_MODULE := NfcReader
LOCAL_SRC_FILES := NfcReader.apk
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_DEX_PREOPT := false
LOCAL_PRIVILEGED_MODULE := false
include $(BUILD_PREBUILT)
```

放到设备树里，并在 `device.mk` 加：

```makefile
PRODUCT_PACKAGES += NfcReader
```

`NFC` 是 normal 权限，不需要 priv-app，普通应用目录即可。

## 技术说明

- **不用 `enableReaderMode`**：`NfcAdapter.enableReaderMode` / `ReaderCallback` / `FLAG_READER_*` 全是 `@SystemApi`，public SDK 里不存在。本工程用 `enableForegroundDispatch` + `ACTION_TECH_DISCOVERED` + `onNewIntent`，兼容性更好。
- **技术过滤表**：`res/xml/nfc_tech_filter.xml` 声明了 9 种技术，配合 `android.nfc.action.TECH_DISCOVERED` 的 meta-data 使用。
- **`android.nfc.extra.TECH`**：`NfcAdapter.EXTRA_TECH` 同样是 `@SystemApi`，这里直接用字面量字符串。
- **Mifare Classic 块号计算**：`MifareClassic.getBlockIndex()` 是隐藏 API，工程内自己算了扇区→块的映射（4K 卡前 32 扇区每扇区 4 块，之后每扇区 16 块）。
- **零第三方依赖**：Material 3 的圆角卡片、elevation、水波纹全部用平台原生 API（`GradientDrawable` / `RippleDrawable` / `setElevation`）手写，不引 AndroidX，编译秒级完成。

## 已知限制

- 加密的 Mifare Classic 扇区需要已知 KeyA，工程内置的是常见出厂密钥；非常规密钥需要自己扩展。
- NDEF 检测失败（`NFA_NDEF_DETECT_EVT status=0x3`）对普通门禁卡是正常现象，说明这张卡本来就没写 NDEF。
- 部分厂商 ROM 会限制后台 NFC 调度，需要允许自启动。

## 许可证

MIT
