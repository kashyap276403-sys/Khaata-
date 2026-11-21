package com.aurax.guard

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Takes ONE photo with the front camera (no preview on screen), stamps the
 * time and [label] on it, and saves it to the gallery under
 * Pictures/auraXGuard. [capture] reports back exactly once.
 */
/** One saved intruder photo. */
data class Photo(val uri: android.net.Uri, val name: String, val timeMs: Long)

class SelfieCapture(private val ctx: Context, private val label: String = "") {
    companion object {
        const val FOLDER = "Pictures/auraXGuard"

        /** Same as [capture] but tries once more if the camera was busy or slow. */
        fun run(ctx: Context, label: String, onDone: (Boolean, String) -> Unit) {
            SelfieCapture(ctx, label).capture { ok, msg ->
                if (ok || msg.contains("permission", ignoreCase = true)) {
                    onDone(ok, msg)
                } else {
                    Prefs.log(ctx, "Pehli koshish fail ($msg). Dobara try...")
                    Handler(android.os.Looper.getMainLooper()).postDelayed({
                        SelfieCapture(ctx, label).capture(onDone)
                    }, 800)
                }
            }
        }

        /** All intruder photos (gallery folder + app-private folder), newest first. */
        fun listPhotos(ctx: Context): List<Photo> {
            val out = mutableListOf<Photo>()
            try {
                ctx.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED,
                    ),
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf("$FOLDER/%"),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        out.add(
                            Photo(
                                android.content.ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    c.getLong(0),
                                ),
                                c.getString(1) ?: "photo",
                                c.getLong(2) * 1000,
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            try {
                privateDir(ctx).listFiles()?.forEach {
                    out.add(Photo(android.net.Uri.fromFile(it), it.name, it.lastModified()))
                }
            } catch (_: Exception) {
            }
            return out.sortedByDescending { it.timeMs }
        }

        fun countPhotos(ctx: Context): Int = listPhotos(ctx).size

        fun deletePhoto(ctx: Context, p: Photo): Boolean = try {
            if (p.uri.scheme == "file") {
                java.io.File(p.uri.path!!).delete()
            } else {
                ctx.contentResolver.delete(p.uri, null, null) > 0
            }
        } catch (_: Exception) {
            false
        }

        fun privateDir(ctx: Context) = java.io.File(ctx.filesDir, "intruders").apply { mkdirs() }
    }

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
                set(CaptureRequest.JPEG_QUALITY, 90.toByte())
            }
            s.capture(req.build(), null, handler)
        } catch (e: Exception) {
            finish(false, "Photo nahi kheench payi: ${e.message}")
        }
    }

    private fun onJpeg(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val raw = try {
            val buf = image.planes[0].buffer
            ByteArray(buf.remaining()).also { buf.get(it) }
        } finally {
            image.close()
        }
        try {
            val bytes = stamp(raw)
            val name = "intruder_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
            if (!Prefs.photosInGallery(ctx)) {
                java.io.File(privateDir(ctx), name).writeBytes(bytes)
                finish(true, "Photo app ke andar save hui (gallery me nahi): $name")
                return
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
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
            finish(true, "Photo gallery me save hui: $FOLDER/$name")
        } catch (e: Exception) {
            finish(false, "Save error: ${e.message}")
        }
    }

    /** Turns the photo upright and prints date, time and reason on it. */
    private fun stamp(jpeg: ByteArray): ByteArray {
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
        val m = Matrix().apply { postRotate(sensorOrientation.toFloat()) }
        val upright = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val out = upright.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val size = out.width / 22f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size
        }
        val edge = Paint(fill).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = size / 6f
        }
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date())
        val lines = listOfNotNull(time, label.ifBlank { null })
        var y = out.height - size * 0.6f - (lines.size - 1) * size * 1.2f
        for (l in lines) {
            canvas.drawText(l, size / 2f, y, edge)
            canvas.drawText(l, size / 2f, y, fill)
            y += size * 1.2f
        }
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        if (upright !== src) src.recycle()
        return bos.toByteArray()
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
