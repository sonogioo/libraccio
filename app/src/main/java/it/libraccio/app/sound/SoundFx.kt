package it.libraccio.app.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

enum class Sfx { THUMP, TOC, PAGE, CHIME }

/**
 * Effetti sonori sintetizzati al volo: niente file audio da includere.
 * THUMP = libro rimesso sullo scaffale, TOC = codice scansionato,
 * PAGE = pagine sfogliate, CHIME = libro finito.
 */
class SoundFx(private val enabled: () -> Boolean) {
    private val rate = 22050
    private val tracks = HashMap<Sfx, AudioTrack>()
    private val lock = Any()

    fun play(sfx: Sfx) {
        if (!enabled()) return
        Thread {
            runCatching {
                synchronized(lock) {
                    val track = tracks.getOrPut(sfx) { build(synth(sfx)) }
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    track.reloadStaticData()
                    track.play()
                }
            }
        }.start()
    }

    private fun build(data: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        return track
    }

    private fun synth(sfx: Sfx): ShortArray {
        val rnd = Random(42)
        fun gen(duration: Double, f: (Double) -> Double): ShortArray {
            val n = (duration * rate).toInt()
            return ShortArray(n) { i ->
                val t = i.toDouble() / rate
                (f(t).coerceIn(-1.0, 1.0) * 32767 * 0.8).toInt().toShort()
            }
        }
        return when (sfx) {
            Sfx.THUMP -> {
                var lp = 0.0
                gen(0.22) { t ->
                    val noise = rnd.nextDouble() * 2 - 1
                    lp += (noise - lp) * 0.08
                    (sin(2 * PI * 90 * t) * 0.9 + lp * 1.6) * exp(-t * 20) * (1 - exp(-t * 800))
                }
            }
            Sfx.TOC -> gen(0.07) { t ->
                (sin(2 * PI * 1750 * t) * 0.6 + sin(2 * PI * 2630 * t) * 0.3) * exp(-t * 70)
            }
            Sfx.PAGE -> {
                var lp = 0.0
                gen(0.32) { t ->
                    val noise = rnd.nextDouble() * 2 - 1
                    lp += (noise - lp) * 0.35
                    val env = sin(PI * t / 0.32).pow(2)
                    lp * env * 0.7 * (0.6 + 0.4 * sin(2 * PI * 31 * t))
                }
            }
            Sfx.CHIME -> gen(0.9) { t ->
                (sin(2 * PI * 880 * t) * 0.5 + sin(2 * PI * 1318.5 * t) * 0.3 + sin(2 * PI * 1760 * t) * 0.15) *
                    exp(-t * 4.5) * (1 - exp(-t * 300))
            }
        }
    }
}
