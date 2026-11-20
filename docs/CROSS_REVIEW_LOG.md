# 卡牌实现 · 7 轮交叉 Review 日志

> 目标：以「分析 → 建议 → 修改 → 复查」流程循环 7 轮，每轮从独立角度审查当前卡牌实现，
> 找出功能正确性 / 边界条件 / 性能 / 安全性 / 兼容性 / 可维护性问题，修复后由独立 agent 复查。
>
> 隔离规则：**实施修改的子 agent 不得参与同一轮的 review / 复查。**
> 基线：`feat/card-image-support` @ `bbb51a5`，起始工作区干净。

---

## 轮次与角度

| 轮次 | 角度 | 状态 |
|---|---|---|
| 1 | 复习算法正确性 | ✅ 完成（复查通过） |
| 2 | 数据正确性与兼容性 | ✅ 完成（复查通过） |
| 3 | UI 交互与手势 | ✅ 完成（复查通过） |
| 4 | 安全性 | ✅ 完成（复查通过） |
| 5 | 性能与缓存 | ✅ 完成（复查通过，1 项运行验证保留） |
| 6 | 可维护性与边界健壮性 | ✅ 完成（复查通过，1 项本轮引入回归移交第 7 轮） |
| 7 | 全量回归终审 | ✅ 完成（复查通过） |

---

## 第 1 轮 · 复习算法正确性

**分析（只读 agent `r1-reviewer`）**：发现 8 项，其中 B1(高)=同日判定用 state 而非 daysElapsed；B2(中)=short-term SInc 门限应为 G>=2；B3=SM-2 AGAIN 是否更新 EF（与官方有分歧）；B4~B8=低优先级公式细节与健壮性。

**建议（主 agent 裁决）**：
- B1/B2 → 修（两轮独立确认 + 官方 FSRS-6 依据）。
- B3 → **不修**：项目既有决策"AGAIN 不更新 EF"，代码注释明确，属设计权衡（标准 SM-2 与 Anki 变体本有分歧），不推翻。
- B4~B8 → 记为低优先级未解决项（均值回归 D0 clamp、参数校验、Adapter easeFactor 语义、lapses 重复计数）。

**修改（implement agent `r1-implementer`，独立于分析 agent）**：
- FSRSAlgorithm.kt：三处稳定性计算统一为 `when{ daysElapsed<=0 -> short-term; AGAIN -> forget; else -> recall }`；SInc 门限 `>=3→>=2` + 注释；新增 `safeStability()` 并替换 5 处 `max(stability,1e-6)`。
- FSRSAlgorithmTest.kt：新增 4 个回归测试（同日 GOOD/AGAIN、隔天 GOOD、同日 HARD）。
- 验证：`:shared:desktopTest` **76 tests / 0 fail / 1 skipped**（FSRSAlgorithmTest 16/16 通过）。

**复查（独立 agent `r1-rechecker`）**：**通过**。4 修复点正确完整，FSRS-6 语义符合，负数 daysElapsed 安全，SM2 未改（符合 B3 裁决），无回归、无越界改动。

---

## 第 2 轮 · 数据正确性与兼容性

**分析（只读 agent `r2-reviewer`）**：发现 BUG-1(high)=updateCard 的 metadata remove no-op；BUG-2(中高)=importData schemaVersion when 值冲突；BUG-3(中)=严格 Json 吞异常丢图；BUG-4(中)=设置页导入丢复习记录；附带=nextReviewAt 可空性。

**建议（主 agent 裁决）**：
- BUG-1/BUG-3 → 修（真 bug；BUG-3 修复零副作用）。
- BUG-2 → **不修**：涉及 version.properties/BackupRestoreService 的数据格式契约变更，跨版本导入兼容风险，历史从未发布 v2 → 记为待用户决策项。
- BUG-4 → **不修**：改 guard 会改变产品行为（拒绝 backup 导入）→ 记为产品决策项。
- 附带项 → 先核实再定。

**修改（implement agent `r2-implementer`，独立于分析 agent）**：
- CardViewModel.kt：`card.metadata + mutableMapOf{}` → `card.metadata.toMutableMap().apply{}`，补 hcenter/vcenter 的 else remove，保留未知键。
- SqlDelightRepositories.kt：新增 `lenientJson`；4 处 decode + encode 改用；try/catch 保留。
- CardMediaSerializationTest.kt：补 2 个宽松 Json 测试。
- 核实项：next_review_at 因 SELECT 的 `IS NOT NULL` 生成非空类型，非 bug，未改。
- 验证：`:shared:desktopTest` **78 tests / 0 fail / 1 skipped**，双端编译通过。

**复查（独立 agent `r2-rechecker`）**：**通过**。2 修复点正确无副作用，核实项判断有生成代码支撑，范围无越界（SM2/ExportManager/version.properties/SettingsScreen 均未动），第 1 轮改动完好。3 个低优先级观察项（metadata 测试名、encode 冗余）不构成回归。

---

## 第 3 轮 · UI 交互与手势

**分析（只读 agent `r3-reviewer`）**：发现 BUG-1(P1)=滑动方向 offset/velocity 用 `||` 冲突致误判；BUG-2=onDragEnd 缺 imageViewerOpen 屏蔽；BUG-3=vCenter+有图卡 Center 退化贴顶；BUG-4(P1)=富文本 SPLIT 切卡串卡/FLIP 闪空白；BUG-5=多选题前导空格判定 + selectedOptions 跨卡残留。

**建议（主 agent 裁决）**：5 项全修（真 bug / 体验 bug，小规模确定性改动）；设计权衡不修：CLOZE hint/:: 截断、CLOZE 替换顺序、✓ 符号、canGoBack=false 行为。

**修改（implement agent `r3-implementer`，独立于分析 agent）**：
- StudyScreen.kt：onDragEnd 用主导因素（超阈倍数）统一 goRight、加 viewer 屏蔽回弹；vCenter 改 BoxWithConstraints + `.heightIn(min=viewportHeight)`（置于 verticalScroll 内侧）。
- CardComponents.kt：RichTextCardFace 用 `key(html)` + ready 门控；多选题先 trim 再 filter、selectedOptions 用 `remember(card.id)`。
- 验证：`:shared:desktopTest` + 双端编译 **BUILD SUCCESSFUL**（1m45s），无编译错误。

**复查（独立 agent `r3-rechecker`）**：**通过**。5 点逻辑正确；核心隐患（除零为 Float 不崩溃、BoxWithConstraints maxHeight 有界、key 变化会重建、ready 不会永假、index 一致、非裸 return）经推演均排除；无越界。**注：本轮无 GUI 运行验收（:composeApp:run exit 1 遗留），仅静态推演 + 编译。**

---

## 第 4 轮 · 安全性

**分析（只读 agent `r4-reviewer`）**：发现 V1=skill http 前导空格绕过；V2=https→http 重定向降级 + SSRF；V3=不可信 media 路径读本地任意文件；其余（HTML XSS、算法 JSON 注入、提示词注入）判为误报/设计权衡。

**建议（主 agent 裁决）**：
- V1/V2 → 修（明确安全漏洞，局部加固）。
- V3 → **拆分**：AI 生成路径加协议白名单（AI 是明确不可信源，纯加固）→ 修；BackupRestoreService 路径校验、parseImageRefs 过滤 LOCAL_URI → **不修**（触及本地图片支持核心功能与备份恢复兼容性，属产品决策）。

**修改（implement agent `r4-implementer`，独立于分析 agent）**：
- build_deck.py：media_items 局部 `.strip()` + 协议白名单（拒 file/content/data///、/、Windows 盘符）；全局 text() 未改；test_skill.py 补 2 反例。
- 两平台 PlatformImageLoader：2xx 分支内读 body 前加最终 URL scheme 校验。
- AiCardGenerator：新增 isAllowedAiMediaPath() helper（相对路径/https 放行，其余拒）。
- 验证：Python **79 tests OK**，Gradle **BUILD SUCCESSFUL**（2m17s），安装副本 uninstall→install + diff SYNC-OK。

**复查（独立 agent `r4-rechecker`）**：**通过**。3 修复点正确、位置准、无误伤合法图片；范围无越界，安装副本一致。观察项（均前几轮继承）：build_deck.py `startswith("http")` 会误拒 `httpnotes.png`（移交第 6 轮）、`..` 子串误伤、UNC 反斜杠未挡。

---

## 第 5 轮 · 性能与缓存

**分析（只读 agent `r5-reviewer`）**：P1-1=Desktop 大图无降采样+OOM 未捕获；P2-1=缓存被 WebDAV 全量扫描上传；P2-2=伪 LRU(FIFO)+自淘汰缺陷；P2-3=磁盘缓存无上限/非原子写/无 single-flight/损坏永久污染；P3-1=clearCache 从未调用；P3-2=splash 主线程解码。

**建议（主 agent 裁决）**：P1-1/P2-1/P2-2/P2-3(部分)/P3-1 → 修；P3-2（splash 后台化，超本轮 core）+ single-flight + TTL/总量上限 → 记为后续。

**修改（implement agent `r5-implementer`，独立于分析 agent）**：
- PlatformImageLoader.desktop.kt：Skia Codec 降采样（MAX_DECODE_EDGE=2048，按 2 的幂），platformLoadImage/Decode 共用 helper，catch→Throwable。经 2 轮试错用 skiko 0.144.6 实际 API（Codec.makeFromData + readPixels 缩放 + makeFromBitmap）。
- Platform.desktop/android.kt：scanMediaDirectory/Raw 排除 cache/ 前缀（normalize+replace+startsWith）。
- CardImageLoader.kt：LinkedHashMap accessOrder=true 真 LRU；淘汰跳过新 key + size>1；读缓存校验非空/<=MAX 否则删；原子写 writeMediaFileAtomically。
- WebDavConfigScreen.kt：syncMedia 写回调加 CardImageLoader.clearCache() 接线。
- 验证：Gradle **BUILD SUCCESSFUL**（1m56s，第 1 轮 Codec API 失败第 2 轮修复）。

**复查（独立 agent `r5-rechecker`）**：**通过但有运行验证保留**。5 点静态核对正确，Skia readPixels 缩放语义经 skiko 官方 KDoc 证实。⚠️ 保留项：整数除法 floor 的缩放尺寸与 Skia 取整在"宽高非 sample 整数倍"边界需真机验证（失败降级占位图，非崩溃）。Android main 未单独编译（改动为同包私有 helper + filter，语法同 desktop）。

---

## 第 6 轮 · 可维护性与边界健壮性

**分析（只读 agent `r6-reviewer`）**：P1=i18n 硬编码中文 Failure.reason；P2=编辑器无去重；P3=URL 只校验前缀无 host；P4=extractMediaFileName 静默失败+未用文案；P5=AI systemPrompt 传入被忽略；P6=fallback 吞异常；P7=SPECIFY_BOTH 校验绕过；P8=skill http 误拒 httpnotes.png（上轮移交）；P9=.. 子串误伤。

**建议（主 agent 裁决）**：P1/P2/P3/P4(部分)/P5/P6/P8/P9 → 修；P7 → **不修**（resolveEntities 重构回归面大且 UI 层已有校验，记为后续）；P4 新文案 cardImageAddFailed → 延后。

**修改（implement agent `r6-implementer`，独立于分析 agent）**：
- CardImageLoader.kt/CardImageViews.kt：FailureReason 枚举类型化，UI 按 reason 映射 strings。
- I18nStrings.kt + 5 份 Strings：补 cardImageLoadFailed/cardImageDecodeFailed（先接口后实现）。
- CardImageEditor.kt：两处去重守卫、HTTPS_IMAGE_URL 正则、extractMediaFileName 容错、cardImageLocalHint 展示。
- AiCardGenerator.kt：systemPrompt 优先 ifBlank 兜底、fallback catch 记录 onLog。
- build_deck.py+test：startswith("https://") 修正、.. 按路径段判断、2 正例。
- 验证：Python **81 tests OK**，Gradle **BUILD SUCCESSFUL**（RegexOption 枚举名 2 轮试错后通过）；安装副本同步一致。

**复查（独立 agent `r6-rechecker`）**：**通过**。P1-P9 核心满足，5 语言 i18n 真实补齐，.. 按段判断未放松防护，范围无越界。⚠️ **本轮引入 1 回归（移交第 7 轮）**：HTTPS 正则过严，误拒 IP/localhost 图床（192.168.1.1、localhost:8080）与无路径 query（example.com?x=1）——原 startsWith 允许，属收紧过度。另有 2 低优先级观察（URL 去重静默、foo( 裸名边界）。

---

## 第 7 轮 · 全量回归终审

**分析（只读 agent `r7-reviewer`）**：跨模块一致性核对通过；确认必须修=第 6 轮移交的 HTTPS 正则回归；建议修=去重反馈/UNC 反斜杠/foo( 边界/FSRS 过时注释；测试缺口=HTTPS 正则参数化测试。

**建议（主 agent 裁决）**：必修 + 4 项建议 + 补测试 → 全修；三处 URL 校验标准统一（跨模块契约）+ Skia 真机验证 → 记为后续。

**修改（implement agent `r7-implementer`，独立于分析 agent）**：
- CardImageEditor.kt：放宽 HTTPS_IMAGE_URL 正则（允许 IP/localhost/端口/无路径 query，拒空 host/http/空格/@混淆，保留 IGNORE_CASE，private→internal）；URL 重复给 urlError 反馈；extractMediaFileName 裸名加括号边界。
- build_deck.py + test：禁用前缀加 UNC `\\`，补反例。
- FSRSAlgorithmAdapter.kt：过时注释改为 ReviewService/last_reviewed_at（仅注释）。
- 新增 CardImageEditorRegexTest.kt：HTTPS 正则参数化测试（5 允许 + 4 拒绝）。
- 验证：Python **81 tests OK**，Gradle **BUILD SUCCESSFUL**，额外 `:composeApp:desktopTest` **BUILD SUCCESSFUL**（新测试通过）；安装副本同步一致。

**复查（独立 agent `r7-rechecker`）**：**通过**。6 项修改正确，正则 9 用例推演全对，5 允许+4 拒绝覆盖；全局回归确认 7 轮所有关键修复点保留（FSRS when / toMutableMap / lenientJson / goRight / key(html) / strip / scheme 校验 / accessOrder / FailureReason 逐一抽查在），无误删、无新增 TODO、无覆盖。2 条观察项（测试跨包依赖 friend-module、URL_HTTPS 较宽）记为后续，不阻断。

---

## 汇总

### 流程合规性
- **7 轮全部执行**：每轮严格「分析（只读 review agent）→ 建议（主 agent 裁决）→ 修改（implement agent）→ 复查（独立 rechecker agent）」。
- **隔离规则满足**：每轮的 review / rechecker 均为只读 Explore agent，implement 为独立 general-purpose agent，**三者是不同实例**；没有任何 implement agent 参与同轮 review。
- 共启动 **21 个 agent**（7 轮 × 3）。每轮复查均通过；第 1 轮 1 项设计权衡不修、第 2 轮 2 项决策项不修、第 4 轮 1 项拆分不修、第 5 轮 1 项运行验证保留、第 6 轮 1 项回归移交第 7 轮并修复。

### 已修复问题（20 项）
| 轮次 | 已修 |
|---|---|
| 1 算法 | FSRS 同日判定改用 daysElapsed（含 AGAIN）；SInc 门限 G>=2；NaN 防御 safeStability；4 个回归测试 |
| 2 数据 | updateCard metadata remove no-op；严格 Json 丢失风险（lenientJson）；2 个序列化测试 |
| 3 交互 | 滑动方向 offset/velocity 冲突（统一 goRight）；onDragEnd viewer 屏蔽；vCenter+有图卡居中；富文本切卡串卡（key(html)+ready）；多选题 trim+切卡重置 |
| 4 安全 | skill http 前导空格绕过（局部 strip+白名单）；https→http 重定向降级（双平台 scheme 校验）；AI 媒体协议白名单；2 个安全反例 |
| 5 性能 | Desktop 大图 Skia 降采样+catch Throwable；缓存排除 WebDAV 同步；真 LRU+防自淘汰；缓存损坏校验+原子写；clearCache 接线 |
| 6 可维护 | FailureReason 枚举+i18n 5 语言；编辑器去重/URL host 校验/文件名容错/hint 展示；AI systemPrompt 生效+fallback 日志；skill http 误拒修复 + .. 按段判断；2 正例 |
| 7 终审 | HTTPS 正则回归放宽（IP/localhost/端口/query）+9 用例测试；去重反馈；foo( 边界；UNC 反斜杠；FSRS 过时注释 |

### 未解决风险（如实标注）
- ⚠️ **真机 GUI 运行验证未做**：`:composeApp:run` exit 1 遗留问题未根治。第 3 轮 UI 交互、第 5 轮 Skia 缩放取整边界均只做静态推演 + 编译验证。
- 🟡 **第 5 轮保留项**：Skia 整数除法缩放尺寸在"宽高非 sample 整数倍"边界需真机验证（失败表现为降级占位图，非崩溃）。
- 🟡 **决策项（本流程未改，需用户定夺）**：BUG-2 schemaVersion when 冲突（数据格式契约变更）；BUG-4 设置页导入丢复习记录（产品行为）；V3 备份 media 路径校验（本地图片支持兼容性）；P7 AI SPECIFY_BOTH 校验绕过。
- 🟡 **后续优化**：single-flight / 缓存 TTL+总量上限 / splash 解码后台化 / 三处 URL 校验标准统一 / cardImageAddFailed 文案 / URL_HTTPS 正则收紧。

### 验证结论
- Gradle 全量：**BUILD SUCCESSFUL**（2m5s）；`:shared:desktopTest` 78 tests/0 fail/1 skip，`:composeApp:desktopTest` 5 tests/0 fail（新增正则测试）。
- Python skill：**81 tests OK**（skipped=3）；安装副本 uninstall→install + diff 一致。
- Android `compileDebugKotlinAndroid` 通过（未做真机安装）。
- 变更：25 文件修改 + 2 新增（测试 + 本日志），+429/−174。**未提交、未推送**（等待用户确认）。
