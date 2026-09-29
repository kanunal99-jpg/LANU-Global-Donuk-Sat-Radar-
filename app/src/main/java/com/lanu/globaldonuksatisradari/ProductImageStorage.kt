package com.lanu.globaldonuksatisradari

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.UUID

data class PendingProductCameraCapture(
    val file: File,
    val uri: Uri,
)

class ProductImageStorage(context: Context) {
    private val appContext = context.applicationContext
    private val imageDir = File(appContext.filesDir, "product_images").apply { mkdirs() }
    private val cameraDir = File(appContext.cacheDir, "product_camera").apply { mkdirs() }

    fun importFromPicker(uri: Uri): String {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase()
        require(mime in SUPPORTED_MIME_TYPES) { "Desteklenmeyen görsel türü: ${mime ?: "bilinmiyor"}" }
        val extension = extensionFor(mime!!)
        val destination = newDestination(extension)
        val stream = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Seçilen görsel açılamadı.")
        stream.use { copyChecked(it, destination) }
        return Uri.fromFile(destination).toString()
    }

    fun createCameraCapture(): PendingProductCameraCapture {
        val file = File.createTempFile("capture_", ".jpg", cameraDir)
        val uri = FileProvider.getUriForFile(
            appContext,
            appContext.packageName + ".fileprovider",
            file,
        )
        return PendingProductCameraCapture(file, uri)
    }

    fun finalizeCameraCapture(capture: PendingProductCameraCapture): String {
        require(capture.file.exists() && capture.file.length() > 0L) { "Kamera görsel üretmedi." }
        val destination = newDestination("jpg")
        capture.file.inputStream().use { copyChecked(it, destination) }
        capture.file.delete()
        return Uri.fromFile(destination).toString()
    }

    fun discardCameraCapture(capture: PendingProductCameraCapture?) {
        capture?.file?.delete()
    }

    fun deleteOwned(reference: String?) {
        val file = ownedFile(reference) ?: return
        file.delete()
    }

    fun isOwned(reference: String?): Boolean = ownedFile(reference) != null

    private fun ownedFile(reference: String?): File? {
        if (reference.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val file = File(path)
        val root = imageDir.canonicalFile
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return canonical.takeIf { candidate ->
            candidate.path.startsWith(root.path + File.separator) && candidate.isFile
        }
    }

    private fun newDestination(extension: String): File =
        File(imageDir, UUID.randomUUID().toString() + "." + extension)

    private fun copyChecked(input: InputStream, destination: File) {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            destination.outputStream().use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_IMAGE_BYTES) { "Görsel en fazla 12 MB olabilir." }
                    output.write(buffer, 0, read)
                }
            }
            require(total > 0L) { "Görsel dosyası boş." }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private fun extensionFor(mime: String): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        else -> error("Desteklenmeyen görsel türü")
    }

    companion object {
        const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L
        private val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif",
        )
    }
}
