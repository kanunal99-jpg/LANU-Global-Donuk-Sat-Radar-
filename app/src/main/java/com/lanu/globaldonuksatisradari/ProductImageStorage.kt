package com.lanu.globaldonuksatisradari

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class ProductImageSource {
    NONE,
    URL,
    GALLERY,
    CAMERA,
}

data class ProductCameraCapture(
    val uri: Uri,
    val tempPath: String,
)

class ProductImageStorage(private val context: Context) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }
    private val cameraDirectory = File(appContext.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }

    fun importFromUri(uri: Uri, source: ProductImageSource): String {
        require(source == ProductImageSource.GALLERY || source == ProductImageSource.CAMERA) {
            "Yerel görsel kaynağı galeri veya kamera olmalıdır."
        }
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)
        if (!mime.isNullOrBlank()) {
            require(mime in SUPPORTED_MIME_TYPES) {
                "Desteklenmeyen görsel türü: $mime"
            }
        }
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            val length = descriptor.length
            require(length < 0L || length <= MAX_IMAGE_BYTES) {
                "Ürün görseli en fazla ${MAX_IMAGE_BYTES / (1024 * 1024)} MB olabilir."
            }
        }

        val extension = extensionForMime(mime)
        val target = File(imageDirectory, "product-${UUID.randomUUID()}.$extension")
        resolver.openInputStream(uri)?.use { input ->
            copyValidated(input, target)
        } ?: error("Seçilen görsel okunamadı.")
        return target.absolutePath
    }

    fun createCameraCapture(): ProductCameraCapture {
        val temp = File(cameraDirectory, "capture-${UUID.randomUUID()}.jpg")
        if (!temp.exists()) temp.createNewFile()
        val uri = FileProvider.getUriForFile(
            appContext,
            appContext.packageName + ".fileprovider",
            temp,
        )
        return ProductCameraCapture(uri = uri, tempPath = temp.absolutePath)
    }

    fun persistCameraCapture(tempPath: String): String {
        val temp = File(tempPath)
        require(temp.exists() && temp.isFile) { "Kamera çıktısı bulunamadı." }
        require(temp.length() in 1..MAX_IMAGE_BYTES) {
            "Kamera görseli boş veya ${MAX_IMAGE_BYTES / (1024 * 1024)} MB sınırını aşıyor."
        }
        val target = File(imageDirectory, "product-${UUID.randomUUID()}.jpg")
        temp.inputStream().use { input -> copyValidated(input, target) }
        temp.delete()
        return target.absolutePath
    }

    fun deleteLocalImage(path: String?) {
        val file = path?.takeIf(String::isNotBlank)?.let(::File) ?: return
        val canonicalRoot = imageDirectory.canonicalFile
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (canonicalFile.parentFile == canonicalRoot) {
            canonicalFile.delete()
        }
    }

    fun discardCameraCapture(tempPath: String?) {
        tempPath?.takeIf(String::isNotBlank)?.let(::File)?.delete()
    }

    private fun copyValidated(input: java.io.InputStream, target: File) {
        try {
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_IMAGE_BYTES) {
                        "Ürün görseli en fazla ${MAX_IMAGE_BYTES / (1024 * 1024)} MB olabilir."
                    }
                    output.write(buffer, 0, read)
                }
                require(total > 0L) { "Ürün görseli boş olamaz." }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun extensionForMime(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    companion object {
        const val MAX_IMAGE_BYTES: Long = 10L * 1024L * 1024L
        private const val IMAGE_DIRECTORY = "product_images"
        private const val CAMERA_DIRECTORY = "product_camera"
        private val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif",
        )
    }
}
