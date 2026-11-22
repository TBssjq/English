package com.kyant.backdrop

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    /**
     * 获取（必要时编译）AGSL 着色器。
     * 返回 null 表示该着色器编译失败（或此前已失败过），调用侧必须退化为
     * 「不带该效果」的绘制，禁止把异常抛到绘制阶段。
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(
        key: String,
        @Language("AGSL") string: String
    ): RuntimeShader?
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    // 已确认编译失败的 key：AGSL 编译结果是确定性的（源码与驱动不变则必然再次失败），
    // 缓存失败标记可避免每帧重试编译，防止低端机/异常驱动上持续卡顿与日志刷屏
    private val failedKeys = mutableSetOf<String>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader? {
        // key 相同的不同 AGSL 源码必须各自编译，否则会取到错误的着色器
        val cacheKey = "$key:${string.hashCode()}"
        runtimeShaders[cacheKey]?.let { return it }
        if (failedKeys.contains(cacheKey)) return null
        // RuntimeShader 构造时会立即编译 AGSL，部分驱动/魔改 ROM 上会抛
        // IllegalArgumentException 或 RuntimeException；而本函数只在 draw() 内被调用，
        // 组合层无法捕获，不拦截就会直接闪退。这里捕获后返回 null 交由调用侧降级。
        return try {
            RuntimeShader(string).also { runtimeShaders[cacheKey] = it }
        } catch (e: Exception) {
            failedKeys.add(cacheKey)
            null
        }
    }

    fun clear() {
        runtimeShaders.clear()
        failedKeys.clear()
    }
}
