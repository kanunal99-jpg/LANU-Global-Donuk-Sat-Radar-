package com.lanu.globaldonuksatisradari

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

data class CameraImageTarget(
    val file: File,
    val uri: Uri,
)

object ProductMediaStore {
    const val MAX_IMAGE_BYTES: Long = 10L * 1024L * 1024L

    fun persistGalleryImage(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        require(mime.startsWith("image/")) { "Seçilen dosya desteklenen bir görsel değil." }

        val target = createPermanentImageFile(context, extensionForMime(mime))
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_IMAGE_BYTES) { "Ürün görseli en fazla 10 MB olabilir." }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("Seçilen görsel açılamadı.")
        require(target.length() > 0L) { "Seçilen görsel boş." }
        return target.toURI().toString()
    }

    fun createCameraTarget(context: Context): CameraImageTarget {
        val directory = imageDirectory(context)
        val file = File(directory, "camera_${UUID.randomUUID()}.jpg")
        if (!file.createNewFile()) error("Kamera görsel dosyası oluşturulamadı.")
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        return CameraImageTarget(file = file, uri = uri)
    }

    fun finalizeCameraImage(context: Context, target: CameraImageTarget): String {
        require(target.file.exists() && target.file.length() > 0L) {
            "Kamera görseli kaydedilemedi."
        }
        require(target.file.length() <= MAX_IMAGE_BYTES) { "Ürün görseli en fazla 10 MB olabilir." }
        val root = imageDirectory(context).canonicalFile
        require(target.file.canonicalFile.parentFile == root) { "Kamera görsel yolu geçersiz." }
        return target.file.toURI().toString()
    }

    fun removeLocalImage(context: Context, reference: String?) {
        val file = localImageFileOrNull(context, reference) ?: return
        runCatching { file.delete() }
    }

    fun validateReference(context: Context, reference: String?) {
        val value = reference?.trim().orEmpty()
        if (value.isBlank()) return
        if (value.startsWith("https://")) return
        val file = localImageFileOrNull(context, value)
            ?: throw IllegalArgumentException("Ürün görseli HTTPS URL veya uygulama içi güvenli görsel olmalıdır.")
        require(file.exists() && file.isFile) { "Ürün görsel dosyası bulunamadı." }
        require(file.length() in 1..MAX_IMAGE_BYTES) { "Ürün görseli boş veya 10 MB sınırını aşıyor." }
    }

    private fun localImageFileOrNull(context: Context, reference: String?): File? {
        val value = reference?.trim().orEmpty()
        if (!value.startsWith("file:")) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val path = uri.path ?: return null
        val file = File(path)
        val root = imageDirectory(context).canonicalFile
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return canonical.takeIf { it.parentFile == root }
    }

    private fun createPermanentImageFile(context: Context, extension: String): File =
        File(imageDirectory(context), "product_${UUID.randomUUID()}.$extension")

    private fun imageDirectory(context: Context): File =
        File(context.filesDir, "product_images").apply { mkdirs() }

    private fun extensionForMime(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
}
