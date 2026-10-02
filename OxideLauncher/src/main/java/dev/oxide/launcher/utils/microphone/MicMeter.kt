/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.utils.microphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import dev.oxide.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

class MicMeter {
    private companion object {
        private const val TAG = "MicMeter"
    }

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    
    private val lock = Any()
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null

    /**
     * Own scope so start()/stop() tear everything down together. A bare CoroutineScope with no Job
     * leaked both the coroutine and the AudioRecord whenever stop() was not reached.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                Logger.error(TAG, "Microphone level loop failed", throwable)
            }
    )

    /**
     * 开始录音，实时返回相对音量值
     * @param onPermissionRequest 没有麦克风权限时，向用户申请权限
     */
    fun start(
        context: Context,
        onLevelUpdate: (Double) -> Unit,
        onPermissionRequest: () -> Unit
    ) {
        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionRequest()
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        job = scope.launch {
            val buffer = ShortArray(bufferSize)
            while (isActive) {
                try {
                    val record = synchronized(lock) { audioRecord } ?: break
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val v = buffer[i].toDouble()
                            sum += v * v
                        }
                        val rms = sqrt(sum / read)

                        val safeRms = max(rms, 1.0)

                        //计算相对分贝值，0 对应完全静音
                        val db = 20 * log10(safeRms)
                        val level = max(db, 0.0)

                        onLevelUpdate(level)
                    }
                    delay(50L.milliseconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // stop() releases the AudioRecord while a read may still be in flight, which
                    // surfaces as IllegalStateException. Swallowing that here used to crash the
                    // process through the global uncaught exception handler.
                    Logger.warning(
                        TAG,
                        "Microphone read failed, stopping the level meter",
                        t
                    )
                    break
                }
            }
        }
    }

    /**
     * 停止麦克风检查
     */
    fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
            val record = audioRecord
            audioRecord = null
            // Releasing can throw when the recorder was never started successfully.
            runCatching { record?.stop() }
            runCatching { record?.release() }
        }
    }
}