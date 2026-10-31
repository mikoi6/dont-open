package com.example.dontopen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 迷宫里的一格。
 * text   —— 显示在图标下面的文字
 * isPath —— 这一格是否通往下一层（每层只有一格为 true）
 */
data class Slot(val text: String, val isPath: Boolean)

/** 一个手机上的应用 */
data class AppEntry(val pkg: String, val label: String)

/**
 * 本地配置。MVP 全部塞进 SharedPreferences，不引数据库。
 */
object Store {

    private const val FILE = "dontopen"
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- 受管控的应用 ----
    fun guarded(c: Context): Set<String> =
        sp(c).getStringSet("guarded", emptySet<String>())?.toSet() ?: emptySet()

    fun setGuarded(c: Context, v: Set<String>) {
        sp(c).edit().putStringSet("guarded", v).apply()
    }

    // ---- 口令词：迷宫里唯一正确的那个文件夹名 ----
    fun keyword(c: Context): String = sp(c).getString("keyword", "番茄") ?: "番茄"
    fun setKeyword(c: Context, v: String) = sp(c).edit().putString("keyword", v).apply()

    // ---- 基础层数 ----
    fun baseDepth(c: Context): Int = sp(c).getInt("depth", 3)
    fun setBaseDepth(c: Context, v: Int) = sp(c).edit().putInt("depth", v).apply()

    // ---- 最后一关的自省问题 ----
    fun reason(c: Context): String =
        sp(c).getString("reason", "现在是几点？这件事真的比它重要吗？")
            ?: "现在是几点？这件事真的比它重要吗？"

    fun setReason(c: Context, v: String) = sp(c).edit().putString("reason", v).apply()

    // ---- 冷静倒计时秒数 ----
    fun calmSec(c: Context): Int = sp(c).getInt("calm", 10)
    fun setCalmSec(c: Context, v: Int) = sp(c).edit().putInt("calm", v).apply()

    // ---- 今天"想开"了几次（跨天自动归零）----
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun todayCount(c: Context): Int =
        if (sp(c).getString("countDate", "") == today()) sp(c).getInt("count", 0) else 0

    fun bumpTodayCount(c: Context): Int {
        val n = todayCount(c) + 1
        sp(c).edit().putString("countDate", today()).putInt("count", n).apply()
        bumpDailyStat(c, today())
        return n
    }

    // ---- 主题：dark | light ----
    fun themeMode(c: Context): String = sp(c).getString("theme", "dark") ?: "dark"
    fun setThemeMode(c: Context, v: String) = sp(c).edit().putString("theme", v).apply()

    // ---- 每日统计历史：存成 "yyyy-MM-dd:count,..." 这种，裁剪保留最近 30 天 ----
    fun dailyStats(c: Context): Map<String, Int> {
        val raw = sp(c).getString("dailyStats", "") ?: ""
        return raw.split(',').mapNotNull { part ->
            val i = part.lastIndexOf(':')
            if (i <= 0) return@mapNotNull null
            val date = part.substring(0, i)
            val n = part.substring(i + 1).toIntOrNull() ?: return@mapNotNull null
            date to n
        }.toMap()
    }

    private fun bumpDailyStat(c: Context, date: String) {
        val stats = dailyStats(c).toMutableMap()
        stats[date] = (stats[date] ?: 0) + 1
        val sorted = stats.entries.sortedBy { it.key }.takeLast(30)
        val s = sorted.joinToString(",") { "${it.key}:${it.value}" }
        sp(c).edit().putString("dailyStats", s).apply()
    }

    // ---- v2：本次会话放行的受管应用包名（穿过迷宫后写入，切走即失效）----
    fun unlockedPkg(c: Context): String? = sp(c).getString("unlocked", null)
    fun setUnlockedPkg(c: Context, pkg: String?) {
        sp(c).edit().apply {
            if (pkg == null) remove("unlocked") else putString("unlocked", pkg)
        }.apply()
    }
}

/**
 * 迷宫生成器 —— 这个文件里最重要的部分。
 *
 * 设计取舍（值得你想清楚为什么）：
 *
 * 1) 为什么不是"随机猜哪个文件夹"？
 *    纯猜会让人卡死、烦躁，最后不是放弃游戏而是放弃这个 App。
 *    摩擦应该来自"需要集中注意力"，不是"运气"。
 *
 * 2) 所以路径格用"口令词"，干扰格用形近词
 *    keyword = "番茄"
 *    干扰项 = 番茄酱 / 番茄钟 / 番茄汁 / 小番茄 / 我的番茄 / 番 / 茄 ...
 *    你必须在 9~12 个高度相似的词里，一眼锁定完全一致的那个。
 *    这叫视觉搜索成本，而且不会让你卡死 —— 你总能找到，只是要花几秒。
 *
 * 3) 每次点错，整层重新洗牌
 *    防止你形成肌肉记忆（"第三行第二个"这种）。位置必须不可记忆。
 */
object Maze {

    private val SUFFIX = listOf(
        "钟", "酱", "汁", "派", "小组", "盒子", "计划", "侠",
        "日记", "森林", "工厂", "小站", "手册", "清单", "们"
    )
    private val PREFIX = listOf("小", "老", "大", "新", "我的", "那个", "真", "假")
    private val FILLER = listOf(
        "西红柿", "土豆", "白菜", "洋葱", "南瓜", "青椒",
        "香菇", "冬瓜", "芹菜", "生菜", "豆腐", "茄子"
    )

    /** 造 n 个和 keyword 形近、但绝不等于 keyword 的干扰词 */
    private fun decoys(keyword: String, n: Int, rnd: Random): List<String> {
        val pool = LinkedHashSet<String>()

        SUFFIX.forEach { pool.add(keyword + it) }
        PREFIX.forEach { pool.add(it + keyword) }

        if (keyword.length >= 2) {
            pool.add(keyword.substring(0, keyword.length - 1))   // 去尾字
            pool.add(keyword.substring(1))                       // 去首字
            pool.add("${keyword.first()}${keyword.last()}")      // 只留首尾
        }
        if (keyword.length >= 3) {
            pool.add(keyword.substring(0, 1) + keyword.substring(2)) // 挖掉中间
        }

        FILLER.forEach { pool.add(it) }

        pool.remove(keyword)          // 关键：干扰项绝不能是答案本身

        val list = pool.toMutableList()
        list.shuffle(rnd)

        // 兜底：格子特别多时补足
        var i = 0
        while (list.size < n) list.add("文件夹${i++}")

        return list.take(n)
    }

    /**
     * 造出某一层的所有格子。
     * @param count 格子总数
     */
    fun buildLayer(keyword: String, count: Int, rnd: Random): List<Slot> {
        val n = count.coerceIn(4, 20)
        val pathPos = rnd.nextInt(n)               // 答案位置随机
        val d = decoys(keyword, n - 1, rnd)
        var di = 0
        return List(n) { i ->
            if (i == pathPos) Slot(keyword, true) else Slot(d[di++], false)
        }
    }

    /**
     * 本次要穿几层 = 基础层数 + min(今天已尝试次数, 5)
     * 这就是"越常想开，门越厚"。
     */
    fun totalDepth(base: Int, todayCount: Int): Int =
        (base + minOf(todayCount, 5)).coerceIn(1, 15)
}

/** 真正去启动目标应用 */
fun launchApp(ctx: Context, pkg: String): Boolean {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

/** 按包名解析应用（拦截时用，拿不到就返回 null） */
fun resolveApp(ctx: Context, pkg: String): AppEntry? = runCatching {
    val pm = ctx.packageManager
    val info = pm.getApplicationInfo(pkg, 0)
    AppEntry(pkg, pm.getApplicationLabel(info).toString())
}.getOrNull()

/** 列出所有有启动入口的应用（要在 IO 线程调用） */
fun loadInstalledApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcher, 0)
    }
    return infos
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.applicationInfo?.packageName ?: return@mapNotNull null
            AppEntry(pkg, ri.loadLabel(pm).toString())
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label }
}
