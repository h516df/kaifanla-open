package com.my.firstapp

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 转盘音效。声音是用代码现场合成的小音调，不用额外的音频文件：
 * tick = 转动时的「咔哒」，ding = 抽中时的「叮～咚」，pop = 按钮「啵」。
 */
class SoundFx(context: Context) {
    var enabled = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val tickId = pool.load(wav(context, "tick", tickWave()), 1)
    private val dingId = pool.load(wav(context, "ding", dingWave()), 1)
    private val popId = pool.load(wav(context, "pop", popWave()), 1)

    fun tick() = play(tickId, 0.35f)
    fun ding() = play(dingId, 0.8f)
    fun pop() = play(popId, 0.6f)

    fun release() = pool.release()

    private fun play(id: Int, volume: Float) {
        if (enabled) pool.play(id, volume, volume, 1, 0, 1f)
    }

    private companion object {
        const val RATE = 44_100

        fun samples(ms: Int, f: (t: Double) -> Double) = DoubleArray(RATE * ms / 1000) { f(it.toDouble() / RATE) }

        fun tickWave() = samples(22) { t -> sin(2 * PI * 2400 * t) * exp(-t * 260) * 0.6 }

        fun popWave() = samples(90) { t ->
            val freq = 500 + 900 * (t / 0.09)
            sin(2 * PI * freq * t) * exp(-t * 40) * 0.6
        }

        /** 两个音：C6 然后 E6，带一点泛音，像风铃 */
        fun dingWave() = samples(900) { t ->
            fun bell(start: Double, freq: Double): Double {
                val x = t - start
                if (x < 0) return 0.0
                return (sin(2 * PI * freq * x) + 0.35 * sin(2 * PI * freq * 2 * x)) * exp(-x * 5)
            }
            (bell(0.0, 1046.5) + bell(0.14, 1318.5)) * 0.35
        }

        fun wav(context: Context, name: String, data: DoubleArray): String {
            val file = File(context.cacheDir, "sfx_$name.wav")
            val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.forEach { pcm.putShort((it.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()) }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.size * 2); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(data.size * 2)
            }
            file.writeBytes(header.array() + pcm.array())
            return file.path
        }
    }
}

val LocalSound = staticCompositionLocalOf<SoundFx?> { null }
