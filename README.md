# LocalReader 本地阅读器 · LocalReader Offline Reader

> **本项目由 AI（DeepSeek）编写生成。**
> **This project was written by AI (DeepSeek).**

一个纯离线、零权限的 Android 本地阅读器。支持 TXT / EPUB / MOBI / AZW3 / PDF。
A fully offline, zero-permission Android reader for TXT / EPUB / MOBI / AZW3 / PDF.

---

## 技术栈 Tech Stack

| 类别 Category | 技术 Technology |
| --- | --- |
| 语言 Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| 架构 Architecture | 单 Activity + MVVM + Repository / Single-Activity + MVVM + Repository |
| 数据库 Database | Room |
| 偏好设置 Preferences | DataStore Preferences |
| 导航 Navigation | Navigation Compose |
| 异步 Async | Coroutines + Flow |
| SDK | minSdk 26 / targetSdk 34 |
| 构建 Build | Gradle Kotlin DSL |

## 功能 Features

- **导入 Import**：SAF `ACTION_OPEN_DOCUMENT` 多选；支持 `.txt/.epub/.mobi/.azw3/.pdf`；复制到私有目录；进度与错误提示。SAF multi-select; copies into app-private storage.
- **TXT**：自动检测 UTF-8/GBK/GB18030/UTF-16 编码，中文不乱码；章节识别，识别不到按 8000 字切分；流式读取防 OOM。Auto encoding detection; chapter detection; streaming reads to avoid OOM.
- **EPUB**：container.xml→OPF→spine→NCX/nav.xhtml；元数据与封面；资源解压并重写为本地路径；防 Zip Slip；屏蔽网络。
- **MOBI / AZW3**：纯 Kotlin 解析 PDB/MOBI 头 + EXTH + PalmDOC LZ77，降级转为内部 HTML 章节阅读（详见下方方案）。
- **PDF**：Android `PdfRenderer`；缩放、分页、页码跳转、夜间反色；进度保存 `pageIndex + zoom`。
- **阅读 Reader**：统一 `ReaderScreen`，按格式分发 TXT / HTML / PDF 三种渲染；目录、书签、进度恢复、点击翻页、屏幕常亮、沉浸式。
- **分组 Groups**：创建/重命名/删除；虚拟分组「全部/最近阅读」；「未分组」= groupId null；删除分组只移书不删文件。
- **书架 Shelf**：列表/网格、封面、搜索、排序、长按多选、批量移动/删除。
- **设置 Settings**：字号、行距、段距、页边距、字体、主题（日间/夜间/护眼）、亮度、翻页模式、常亮、PDF 夜间。

## 目录结构 Project Structure

```
app/src/main/java/com/example/localreader/
├── data/      model · dao · db · repository · settings（Room + DataStore）
├── domain/    BookParser 接口 / parser interface
├── parser/    TxtParser · EpubParser · MobiParser · Azw3Parser · PdfParser · MobiDocument
├── importer/  FileImporter · ImportManager
├── util/      EncodingDetector · ChapterParser · TextPaginator · HtmlResourceExtractor ·
│              ZipSlipSafeExtractor · PdfPageRenderer · TxtNormalizer · TimeUtils · BookFiles
├── ui/        shelf · group · settings · reader · theme · navigation · components
└── LocalReaderApp · MainActivity · di/
```

## 构建 Build

> 仓库不包含 `gradle-wrapper.jar` 与 `local.properties`（已 gitignore）。The repo excludes `gradle-wrapper.jar` and `local.properties`.

```bash
# Android Studio 打开项目根目录，使用 JDK17 同步构建
# 或命令行（需 JDK17 + Android SDK）:
cd LocalReader
echo "sdk.dir=C:/Android/Sdk" > local.properties   # 换成你的 SDK 路径
gradle wrapper --gradle-version 8.7               # 生成 wrapper jar
./gradlew :app:assembleDebug                      # 产物 app/build/outputs/apk/debug/app-debug.apk
```

版本组合 Versions：AGP 8.5.2 / Gradle 8.7 / Kotlin 2.0.20 / KSP 2.0.20-1.0.25 / Compose BOM 2024.09.02 / Room 2.6.1。

## MOBI / AZW3 方案 MOBI / AZW3 Approach

当前为**纯 Kotlin 降级方案**：解析 PDB/MOBI 头 + EXTH 元数据 + PalmDOC LZ77，读取 AZW3 内嵌的 MOBI7 兼容文本并转为内部 HTML 章节。无 MOBI7 文本的个别 KF8 文件会给出明确错误。
This is a pure-Kotlin fallback: it parses the PDB/MOBI header + EXTH metadata + PalmDOC LZ77, reads the embedded MOBI7 text, and converts it into internal HTML chapters. KF8 files without MOBI7 text raise a clear error.

生产级完整支持（完整 KF8 排版/目录/图片）可集成 [libmobi](https://github.com/bfabiszewski/libmobi)（MIT）的 JNI：
For production-grade KF8 support, integrate libmobi (MIT) via JNI:

```kotlin
// app/build.gradle.kts
ndkVersion = "26.1.10909125"
externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
```

```cmake
# CMakeLists.txt
add_library(mobi SHARED mobi_jni.c)
add_subdirectory(libmobi)
target_link_libraries(mobi mobi_static log)
```

降级流程 Fallback：解析失败提示用 Calibre 转 EPUB/MOBI 后再导入。

## 关键假设 Key Assumptions

1. 格式分发以**扩展名**为准（SAF 的 MIME 对电子书格式不可靠）。Format dispatch relies on **file extension** (SAF MIME is unreliable for e-books).
2. 封面/章节 href 为「相对 `files/books` 的路径」；WebView 用 `WebViewAssetLoader` 映射到私有目录，叠加「无 INTERNET 权限」双重断网。Covers/chapter hrefs are relative paths; WebView uses `WebViewAssetLoader` + no INTERNET permission.
3. TXT 导入时归一化为 UTF-8 `content.txt`，章节偏移以归一化字符计，按区间读取防 OOM。
4. HTML 类进度 `charOffset` 存「章节内滚动百分比 × 1000」。
5. 亮度用半透明遮罩模拟（不申请系统设置权限）。

## 权限与隐私 Permissions & Privacy

- 无 INTERNET、无 READ/WRITE_EXTERNAL_STORAGE。No INTERNET, no storage permissions.
- 全部数据本地保存。All data is stored locally.
- WebView 仅加载私有目录，禁用网络。

## 验收清单 Verification Checklist

- [ ] 导入 UTF-8 与 GBK 的 `.txt`，中文不乱码
- [ ] 导入 `.epub`，目录/章节/封面正常
- [ ] 导入 `.mobi`（或含 MOBI7 的 `.azw3`）可阅读；无 MOBI7 的 AZW3 给出明确错误
- [ ] 导入 `.pdf`，分页/缩放/翻页/夜间反色
- [ ] 创建/重命名/删除分组；删除分组后书籍回到「未分组」
- [ ] 打开书阅读，目录跳转；退出重进恢复进度
- [ ] 添加/删除/跳转书签
- [ ] 修改字号/行距/主题/翻页模式
- [ ] 大文件阅读不 OOM
- [ ] 应用信息中确认无任何权限

## 已知限制 Known Limitations

- MOBI/AZW3 为纯文本降级路径，不保留原排版/图片（封面除外）。
- 亮度为遮罩式模拟，非系统级。
- 未开启混淆（`isMinifyEnabled=false`）。

## 许可证 License

MIT — 本项目由 AI 编写，可自由使用与修改。MIT — AI-written, free to use and modify.
