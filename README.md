# 你确定要打开吗（dont-open）

一个用「无限层文件夹」给打开游戏这件事加摩擦的安卓小工具。

---

## 先说清楚四件事，避免你白折腾

**1. 我只堵住了一半的门。**
这个 App 只管「从这个 App 里点进去」的路径。你如果从手机桌面直接点游戏图标，**它拦不住**。堵住这条要靠无障碍服务，属于 v2。

**2. 我在这里没法编译验证。**
当前环境的 Java 是 1.8，没有 Android SDK、没有 Gradle。所以 `android/` 里的代码我一行都没跑过编译。我验证的是两件能验证的事：
- 迷宫生成算法的 5 条不变量（7000 次随机生成全部通过，见 `prototype/maze-test.js`）
- 原型的 JS 语法

Compose 部分如果报错，大概率是拼写/import 级别的，数量应该很少。**第一条编译错误请自己先读一遍再问我**，这是你该练的东西。

**3. 这个工具真正的敌人是你自己，不是代码。**
所有自律工具最后都死在同一个地方：用户觉得烦，然后卸载。所以「摩擦」必须**刚好让人烦到放弃一次，又不至于烦到卸载**。这是产品问题，不是技术问题。

**4. 你已经拥有的东西比你以为的多。**
你有 Java 基础，Kotlin 和 Java 在语法上差不多；你有前端基础，Compose 的声明式写法和 React 一个思路。这个项目不需要你学新语言，只需要学「安卓的权限模型」和「Compose 的状态」。

---

## 目录结构

```
dont-open/
├── prototype/
│   ├── index.html      ← 浏览器里直接打开就能玩，确认手感用
│   └── maze-test.js    ← 迷宫算法的测试，node 跑
├── android/            ← 真正的安卓工程
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradle/wrapper/gradle-wrapper.properties
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/dontopen/
│           │   ├── Config.kt        ← 数据 + 存储 + 迷宫生成器（最重要）
│           │   ├── MainActivity.kt  ← 入口 + 配色 + 两个页面的路由
│           │   ├── HomeScreen.kt    ← 首页：选应用、调参数
│           │   └── MazeScreen.kt    ← 迷宫 + 最后一关
│           └── res/
└── README.md
```

**先打开 `prototype/index.html`**，玩三轮再往下读。你对摩擦的手感没有直觉之前，读代码是浪费。

小技巧：`index.html?demo=maze` 直接跳到迷宫页，`?demo=gate` 直接跳到最后一关，省得每次从头点。

---

## 五分钟跑起来

### 路线 A（备用：让 Android Studio 自己生成骨架）

如果你不想用我给的工程、或 Wrapper 出了意外问题，最稳的做法是**让 Android Studio 自己生成骨架，你只覆盖源码**：

1. Android Studio → **New Project** → **Empty Activity**
2. 填写：
   - Name: `你确定？`
   - Package name: `com.example.dontopen` ← 必须完全一致，否则要改所有文件的 `package` 行
   - Language: **Kotlin**
   - Minimum SDK: **API 26**
   - Build configuration language: **Kotlin DSL**
3. 等 Gradle sync 跑完（第一次会下依赖，比较久）
4. **删掉**模板生成的 `app/src/main/java/com/example/dontopen/MainActivity.kt`（不删会和我给的同名文件冲突）
5. 把 `android/app/src/main/java/com/example/dontopen/` 下的 **4 个 .kt 文件**复制进你的项目同路径目录
6. 打开你项目的 `AndroidManifest.xml`，在 `<manifest>` 标签里、`<application>` 之前，加两行：

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:ignore="QueryAllPackagesPermission" />
<uses-permission android:name="android.permission.VIBRATE" />
```

还要在 `<manifest` 标签上加 `xmlns:tools="http://schemas.android.com/tools"`，否则 `tools:ignore` 报错。

7. Run。装到手机上（模拟器读不到你真实装的游戏，用真机）。

**如果 `LazyVerticalGrid` 报红**，在 `app/build.gradle.kts` 的 dependencies 里加：
```kotlin
implementation("androidx.compose.foundation:foundation")
```

### 路线 B（直接打开我给的工程，推荐）

Gradle Wrapper（`gradle-wrapper.jar` + `gradlew` / `gradlew.bat`）已经补齐，直接开即可。

1. Android Studio → Open → 选 `dont-open/android` 目录
2. 等 Gradle sync 跑完（第一次会下依赖，比较久）
3. Run，装到真机上

**命令行构建验证**（JDK 17 环境下）：

```bash
# Windows
cd android
gradlew.bat assembleDebug

# macOS / Linux
cd android
./gradlew assembleDebug
```

产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。

如果 Android Studio 没有自动生成 `local.properties`（里面是 `sdk.dir=...` 指向你的 SDK），
手动新建一个 `android/local.properties`，内容一行：

```
sdk.dir=C\:\\你的\\Android\\SDK\\路径
```

（Windows 路径里的反斜杠要写成 `\\`，冒号写成 `\:`。）

### 如果「选择要藏起来的应用」里是空的

Android 11+ 有包可见性限制，工程里已经加了 `<queries>` 声明（`AndroidManifest.xml`），
正常情况下能列出所有可启动应用。

如果仍然为空（Redmi / HyperOS / MIUI 常见），去系统里手动放行一次：

> 设置 → 应用设置 → 应用管理 →「你确定？」→ 权限 → 打开「读取应用列表 / 获取应用列表」。

---

## 核心设计：摩擦到底从哪来

这是整个项目里唯一值得你想清楚的部分。`Config.kt` 的 `Maze` 对象只有一百来行，但每个决定都有取舍。

### 机制一：路径格用口令词，干扰格用形近词

```
口令词 = 番茄
这一层的 9 个格子：
  番茄小站   番茄计划   那个番茄
  南瓜       真番茄     ★番茄
  番茄派     小番茄     茄
```

为什么不是「随机猜哪个文件夹」？

因为**纯猜会让人卡死**。卡死的结局不是「我不玩游戏了」，而是「我卸载这个破 App」。摩擦应该来自「需要集中注意力」，不能来自「运气」。

形近词的设计让摩擦变成**视觉搜索成本**：你必须在一堆 `番茄X` 和 `X番茄` 里，一眼锁定完全一致的那一个。你总能找到，只是要花 3~5 秒。这个成本刚好。

### 机制二：点错就整层重新洗牌

位置一变，肌肉记忆就废了。你不能靠「第三行第二个」这种记忆通关，每次都得重新看。

代价是：连续点错两次会很烦。**这是故意的**，但也是风险点——如果实测发现太烦，把 `delay(650)` 调短、或者允许点错不洗牌，都是合理的调节。

### 机制三：越常想开，门越厚

```kotlin
totalDepth = 基础层数 + min(今天已想开次数, 5)
```

今天是第一次想开 → 3 层。今天已经想开 4 次了 → 7 层。

这条是整个设计的灵魂。固定层数会被适应（第三次开始你就不觉得难了），**递增层数不会**——你越想开，成本越高，这是唯一能跟「习惯化」对抗的机制。

上限设 5 是因为：不封顶的话，重度使用那天你会穿 20 层，然后愤怒卸载。

### 机制四：最后一关三重门

```
冷静倒计时 10 秒  →  按住 3 秒不松手  →  屏幕上写着你自己的问题
```

三个叠加的效果是：**从「点一下」变成「持续做一组动作」**。

人在冲动的时候能完成一次点击，但很难完成「等 10 秒 + 按住 3 秒 + 读一句自己写的话」。冲动的时间尺度是 1 秒级的，你把动作拉长到 13 秒，冲动就散了。

那个问题是你自己填的，默认是「现在是几点？这件事真的比它更重要吗？」。**换成对你真正有效的**，比如「你今天说要写的代码写完了吗」。

---

## 三个你应该自己动手改的地方

我故意把可调参数都放在明显的位置，别只是复制粘贴。

### 1. 改摩擦强度（最简单，先练手）

`Config.kt` 的 `Store` 里：

```kotlin
fun baseDepth(c: Context): Int = sp(c).getInt("depth", 3)   // 默认 3 层
fun calmSec(c: Context): Int = sp(c).getInt("calm", 10)     // 默认冷静 10 秒
```

`MazeScreen.kt` 里控制格子数量：

```kotlin
val cellCount = when {
    depth <= 2 -> 6
    depth <= 4 -> 9
    else -> 12
}
```

把它改成 `depth <= 2 -> 9, else -> 15`，感受一下难度曲线怎么变。**改完装到手机上真玩一次**，别只看代码。

### 2. 换干扰词池

`Config.kt` 里：

```kotlin
private val SUFFIX = listOf("钟", "酱", "汁", ...)
private val PREFIX = listOf("小", "老", "大", ...)
```

如果你的口令词是英文（比如 `focus`），中缀后缀规则会产生 `focus钟` 这种怪东西。给英文加一组规则：

```kotlin
private val EN_SUFFIX = listOf("s", "ing", "ed", "er", "ly", "box", "app")
```

想清楚：什么词会让「找到它」变得难但不至于不可能？

### 3. 加一个你自己想要的功能

建议加这个：**「每天第一次打开免费」**。

逻辑：如果 `Store.todayCount(c) == 0`，就让 `totalDepth` 返回 1（直接进最后一关）。理由是人每天第一次想放松是合理的，不该被罚；但第二次开始就该有阻力。

改哪里：`Maze.totalDepth()`。改完自己测一遍边界——`todayCount = 0` 时是不是真的 1 层？

---

## 下一版：堵住桌面那个洞

这是这个工具「有没有用」的分水岭。思路是 `AccessibilityService`，代码骨架：

```kotlin
class GuardService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Store.guarded(this)) return
        if (pkg == packageName) return

        // 用户从桌面点开了受管应用 → 立刻把他拽回迷宫入口
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("forceGate", pkg)
        })
    }
    override fun onInterrupt() {}
}
```

配套要做的：
1. `res/xml/accessibility_service_config.xml` 声明 `typeWindowStateChanged`
2. Manifest 里注册 service + `BIND_ACCESSIBILITY_SERVICE` 权限
3. Manifest 里加 `<queries>` 或保留 `QUERY_ALL_PACKAGES`
4. 首次启动引导用户去「设置 → 无障碍」手动开启

**注意这里有个真实的坑**：上面的写法会让游戏先闪一下再被拽回来，体验很生硬。更好的做法是用 `SYSTEM_ALERT_WINDOW` 权限画一个遮罩层直接盖在游戏上面。这个坑你自己踩一遍会记得很牢。

还有一个更狠的思路：**让这个 App 自己变成桌面（Launcher）**。给 Activity 加 `category.HOME` 的 intent-filter，系统会问你要不要用它当桌面。这样游戏图标根本不在你的桌面上，只能走迷宫。代价是你要把桌面重写一遍——但「藏起来」的效果比任何拦截都彻底。

---

## 已知问题（都别当成 bug 修，是取舍）

| 问题 | 为什么这样 | 什么时候修 |
|---|---|---|
| 桌面直接启动绕过了 | v1 范围就是迷宫本体 | v2 无障碍 |
| 今天次数一进迷宫就 +1，没真的开也算 | 「想开」这个动作已经发生了，就该计入 | 不用修 |
| 层数一多，点错重洗很烦 | 故意的，但需要真机调参 | 玩三天后看数据 |
| 图标是运行时加载的，列表滚动会有点卡 | MVP 没做图片缓存 | 应用少的话不用管 |
| 没做应用列表的「系统应用过滤」 | 宁可多显示也别漏 | 感觉乱再加 |

---

## 最后

先把 `prototype/index.html` 玩三轮，然后回答自己一个问题：

**如果你是用户，玩完第三轮，你是想继续用，还是想卸载？**

如果答案是「想卸载」——回来告诉我，我们改设计，不是改代码。
