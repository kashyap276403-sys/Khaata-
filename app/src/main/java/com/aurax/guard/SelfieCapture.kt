package com.aurax.guard

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Takes ONE photo with the front camera (no preview on screen) and saves it to
 * the gallery under Pictures/auraXGuard. [capture] reports back exactly once.
 */
class SelfieCapture(private val ctx: Context) {
    private val thread = HandlerThread("selfie").apply { start() }
    private val handler = Handler(thread.looper)

    private var finished = false
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var yuvReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var sensorOrientation = 0
    private var onDone: ((Boolean, String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun capture(onDone: (Boolean, String) -> Unit) {
        this.onDone = onDone
        if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            finish(false, "Camera permission nahi hai")
            return
        }
        try {
            val mgr = ctx.getSystemService(CameraManager::class.java)
            val id = mgr.cameraIdList.firstOrNull {
                mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: mgr.cameraIdList.firstOrNull()
            if (id == null) {
                finish(false, "Is phone me camera nahi mila")
                return
            }
            val chars = mgr.getCameraCharacteristics(id)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) {
                finish(false, "Camera ki jaankari nahi mili")
                return
            }
            val jpegSize = map.getOutputSizes(ImageFormat.JPEG)
                .filter { it.width <= 1600 }
                .maxByOrNull { it.width * it.height }
                ?: map.getOutputSizes(ImageFormat.JPEG).first()
            val yuvSize = map.getOutputSizes(ImageFormat.YUV_420_888)
                .filter { it.width <= 640 }
                .maxByOrNull { it.width * it.height }
                ?: map.getOutputSizes(ImageFormat.YUV_420_888).minByOrNull { it.width * it.height }!!

            // Frames used only to let auto-exposure settle; they are thrown away.
            yuvReader = ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 2).also {
                it.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close() }, handler)
            }
            jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1).also {
                it.setOnImageAvailableListener({ r -> onJpeg(r) }, handler)
            }

            handler.postDelayed({ finish(false, "Camera timeout (photo nahi aayi)") }, 9000)
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    camera = cam
                    startSession(cam)
                }

                override fun onDisconnected(cam: CameraDevice) {
                    finish(false, "Camera beech me band ho gaya")
                }

                override fun onError(cam: CameraDevice, error: Int) {
                    finish(false, "Camera error $error (shayad doosri app use kar rahi hai)")
                }
            }, handler)
        } catch (e: Exception) {
            finish(false, "Camera khula nahi: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(cam: CameraDevice) {
        try {
            val yuv = yuvReader!!.surface
            val jpeg = jpegReader!!.surface
            cam.createCaptureSession(
                listOf(yuv, jpeg),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        try {
                            val preview = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(yuv)
                            }
                            s.setRepeatingRequest(preview.build(), null, handler)
                            handler.postDelayed({ takeStill(cam, s, jpeg) }, 1200)
                        } catch (e: Exception) {
                            finish(false, "Preview shuru nahi hua: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        finish(false, "Camera session ban nahi paya")
                    }
                },
                handler,
            )
        } catch (e: Exception) {
            finish(false, "Camera session error: ${e.message}")
        }
    }

    private fun takeStill(cam: CameraDevice, s: CameraCaptureSession, jpeg: android.view.Surface) {
        if (finished) return
        try {
            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jpeg)
                set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                set(CaptureRequest.JPEG_QUALITY, 85.toByte())
            }
            s.capture(req.build(), null, handler)
        } catch (e: Exception) {
            finish(false, "Photo nahi kheench payi: ${e.message}")
        }
    }

    private fun onJpeg(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val bytes = try {
            val buf = image.planes[0].buffer
            ByteArray(buf.remaining()).also { buf.get(it) }
        } finally {
            image.close()
        }
        try {
            val name = "intruder_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/auraXGuard")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                finish(false, "Gallery me save nahi ho paya")
                return
            }
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            finish(true, "Photo gallery me save hui: Pictures/auraXGuard/$name")
        } catch (e: Exception) {
            finish(false, "Save error: ${e.message}")
        }
    }

    @Synchronized
    private fun finish(ok: Boolean, msg: String) {
        if (finished) return
        finished = true
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { yuvReader?.close() } catch (_: Exception) {}
        try { jpegReader?.close() } catch (_: Exception) {}
        val cb = onDone
        thread.quitSafely()
        cb?.invoke(ok, msg)
    }
}
