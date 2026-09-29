package com.lanu.globaldonuksatisradari

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProductCatalogScreen(repository: ProductCatalogRepository) {
    val context = LocalContext.current
    val imageStorage = remember(context) { ProductImageStorage(context) }
    val products by repository.products.collectAsState()
    var query by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Adet") }
    var price by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("TRY") }
    var note by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var imageSource by remember { mutableStateOf<ProductImageSource?>(null) }
    var originalImageRef by remember { mutableStateOf<String?>(null) }
    var pendingCamera by remember { mutableStateOf<PendingProductCameraCapture?>(null) }
    var sourceUrl by remember { mutableStateOf("https://globaldonukgida.com/") }
    var sourceVerified by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }

    fun discardUnsavedReplacement() {
        val current = imageUrl.takeIf { it.isNotBlank() }
        if (current != null && current != originalImageRef) imageStorage.deleteOwned(current)
    }

    fun replaceImage(reference: String, source: ProductImageSource) {
        discardUnsavedReplacement()
        imageUrl = reference
        imageSource = source
        editorError = null
    }

    fun cancelEditor() {
        discardUnsavedReplacement()
        pendingCamera?.let(imageStorage::discardCameraCapture)
        pendingCamera = null
        editorOpen = false
    }

    fun openNew() {
        discardUnsavedReplacement()
        pendingCamera?.let(imageStorage::discardCameraCapture)
        pendingCamera = null
        editingId = null
        name = ""
        category = ""
        unit = "Adet"
        price = ""
        currency = "TRY"
        note = ""
        description = ""
        imageUrl = ""
        imageSource = null
        originalImageRef = null
        sourceUrl = "https://globaldonukgida.com/"
        sourceVerified = false
        editorError = null
        editorOpen = true
    }

    fun openEdit(product: CatalogProduct) {
        discardUnsavedReplacement()
        pendingCamera?.let(imageStorage::discardCameraCapture)
        pendingCamera = null
        editingId = product.id
        name = product.name
        category = product.category
        unit = product.unit
        price = (product.priceMinor / 100.0).toString().replace(".", ",")
        currency = product.currency
        note = product.note.orEmpty()
        description = product.description.orEmpty()
        imageUrl = product.imageUrl.orEmpty()
        imageSource = product.imageSource ?: product.imageUrl
            ?.takeIf { it.startsWith("https://") }
            ?.let { ProductImageSource.URL }
        originalImageRef = product.imageUrl
        sourceUrl = product.sourceUrl ?: "https://globaldonukgida.com/"
        sourceVerified = product.sourceVerifiedAtEpochMs != null
        editorError = null
        editorOpen = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { imageStorage.importFromPicker(uri) }
                .onSuccess { replaceImage(it, ProductImageSource.GALLERY) }
                .onFailure { editorError = it.message ?: "Galeri görseli alınamadı." }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCamera
        pendingCamera = null
        if (capture != null) {
            if (success) {
                runCatching { imageStorage.finalizeCameraCapture(capture) }
                    .onSuccess { replaceImage(it, ProductImageSource.CAMERA) }
                    .onFailure {
                        imageStorage.discardCameraCapture(capture)
                        editorError = it.message ?: "Kamera görseli kaydedilemedi."
                    }
            } else {
                imageStorage.discardCameraCapture(capture)
            }
        }
    }

    fun save() {
        editorError = runCatching {
            if (sourceVerified) {
                require(sourceUrl.isNotBlank()) { "Doğrulanmış ürün için resmî kaynak URL zorunludur." }
            }
            repository.upsert(
                id = editingId,
                name = name,
                category = category,
                unit = unit,
                priceMinor = ProductPrice.parseToMinor(price),
                currency = currency,
                note = note,
                description = description,
                imageUrl = imageUrl,
                sourceUrl = sourceUrl,
                sourceVerifiedAtEpochMs = if (sourceVerified) System.currentTimeMillis() else null,
                imageSource = imageSource,
            )
            if (originalImageRef != imageUrl) imageStorage.deleteOwned(originalImageRef)
            originalImageRef = imageUrl.takeIf { it.isNotBlank() }
            editorOpen = false
        }.exceptionOrNull()?.message
    }

    val normalizedQuery = query.trim().lowercase(Locale("tr", "TR"))
    val filteredProducts = if (normalizedQuery.isEmpty()) products else products.filter {
        it.name.lowercase(Locale("tr", "TR")).contains(normalizedQuery) ||
            it.category.lowercase(Locale("tr", "TR")).contains(normalizedQuery) ||
            it.unit.lowercase(Locale("tr", "TR")).contains(normalizedQuery)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).semantics { testTagsAsResourceId = true },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ürün Kataloğu", style = MaterialTheme.typography.headlineSmall)
                Text("Fiyat, birim, görsel ve kaynak bilgilerini tek ekrandan yönetin.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(products.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text("Kayıtlı ürün", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(filteredProducts.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text("Görünen sonuç", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = ::openNew, modifier = Modifier.fillMaxWidth().testTag("product_add_button")) {
                    Text("Yeni ürün ekle")
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag("product_search"),
            singleLine = true,
            label = { Text("Ürün, kategori veya birim ara") },
        )

        if (filteredProducts.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (products.isEmpty()) "Katalog henüz boş" else "Eşleşen ürün bulunamadı", style = MaterialTheme.typography.titleMedium)
                    Text(if (products.isEmpty()) "İlk ürünü ekleyerek fiyat kataloğunu oluşturmaya başlayın." else "Arama metnini değiştirerek tekrar deneyin.")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filteredProducts, key = { it.id }) { product ->
                ProductCard(product = product, onEdit = { openEdit(product) }, onDelete = { deletingId = product.id })
            }
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = ::cancelEditor,
            modifier = Modifier.testTag("product_editor_dialog").semantics { testTagsAsResourceId = true },
            title = { Text(if (editingId == null) "Yeni Ürün" else "Ürünü Düzenle", modifier = Modifier.testTag("product_editor_open_state")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).testTag("product_editor_content").verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth().testTag("product_name_input"), singleLine = true, label = { Text("Ürün adı *") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Kategori") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Birim") })
                        OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase(Locale.ROOT).take(3) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Para") })
                    }
                    OutlinedTextField(value = price, onValueChange = { price = it }, modifier = Modifier.fillMaxWidth().testTag("product_price_input"), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text("Birim fiyat *") }, placeholder = { Text("Örn. 1250,50") })
                    OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Ürün açıklaması") })

                    Text("Ürün fotoğrafı", style = MaterialTheme.typography.titleSmall)
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Ürün fotoğrafı önizleme",
                            modifier = Modifier.fillMaxWidth().height(170.dp).testTag("product_image_preview"),
                            contentScale = ContentScale.Crop,
                        )
                        val sourceLabel = when (imageSource) {
                            ProductImageSource.URL -> "HTTPS URL"
                            ProductImageSource.GALLERY -> "Galeri • yerel güvenli kopya"
                            ProductImageSource.CAMERA -> "Kamera • yerel güvenli kopya"
                            null -> "Bilinmiyor"
                        }
                        Text("Fotoğraf kaynağı: $sourceLabel", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = if (imageSource == null || imageSource == ProductImageSource.URL) imageUrl else "",
                        onValueChange = { value ->
                            discardUnsavedReplacement()
                            imageUrl = value
                            imageSource = value.trim().takeIf { it.isNotEmpty() }?.let { ProductImageSource.URL }
                            editorError = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("product_image_url_input"),
                        singleLine = true,
                        label = { Text("HTTPS görsel URL") },
                        placeholder = { Text(if (imageSource == ProductImageSource.GALLERY || imageSource == ProductImageSource.CAMERA) "Yerel görsel seçili; URL girerek değiştirin" else "https://...") },
                    )
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("product_image_gallery_button"),
                    ) { Text("Galeriden seç") }
                    OutlinedButton(
                        onClick = {
                            val capture = runCatching { imageStorage.createCameraCapture() }
                                .onFailure { editorError = it.message ?: "Kamera hazırlanamıyor." }
                                .getOrNull()
                            if (capture != null) {
                                pendingCamera = capture
                                runCatching { cameraLauncher.launch(capture.uri) }
                                    .onFailure {
                                        imageStorage.discardCameraCapture(capture)
                                        pendingCamera = null
                                        editorError = it.message ?: "Kamera uygulaması açılamadı."
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("product_image_camera_button"),
                    ) { Text("Fotoğraf çek") }
                    if (imageUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                discardUnsavedReplacement()
                                imageUrl = ""
                                imageSource = null
                                editorError = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("product_image_remove_button"),
                        ) { Text("Fotoğrafı kaldır") }
                    }

                    OutlinedTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Resmî kaynak URL") })
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sourceVerified,
                            onCheckedChange = { sourceVerified = it },
                            modifier = Modifier.testTag("product_source_verified_checkbox"),
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Resmî kaynağı doğruladım", style = MaterialTheme.typography.bodyMedium)
                            Text("İşaretlenmezse ürün kaydı doğrulanmış kaynak olarak etiketlenmez.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Not") })
                    editorError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { Button(onClick = ::save, modifier = Modifier.testTag("product_save_button")) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = ::cancelEditor) { Text("Vazgeç") } },
        )
    }

    val deletingProduct = deletingId?.let { id -> products.firstOrNull { it.id == id } }
    if (deletingProduct != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Ürünü sil") },
            text = { Text("“${deletingProduct.name}” kaydı katalogdan silinsin mi?") },
            confirmButton = {
                Button(onClick = {
                    repository.delete(deletingProduct.id)
                    imageStorage.deleteOwned(deletingProduct.imageUrl)
                    deletingId = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("İptal") } },
        )
    }
}

@Composable
private fun ProductCard(product: CatalogProduct, onEdit: () -> Unit, onDelete: () -> Unit) {
    var imageFailed by remember(product.imageUrl) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            product.imageUrl?.let { url ->
                Box(modifier = Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    if (imageFailed) Text("Ürün görseli yüklenemedi") else AsyncImage(model = url, contentDescription = product.name, modifier = Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop, onError = { imageFailed = true })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    val meta = listOf(product.category, product.unit).filter { it.isNotBlank() }.joinToString(" • ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)
                }
                Text(ProductPrice.formatMinor(product.priceMinor, product.currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            product.imageSource?.let { source ->
                val label = when (source) {
                    ProductImageSource.URL -> "URL"
                    ProductImageSource.GALLERY -> "Galeri"
                    ProductImageSource.CAMERA -> "Kamera"
                }
                Text("Görsel kaynağı: $label", style = MaterialTheme.typography.labelSmall)
            }
            product.description?.let { HorizontalDivider(); Text(it, style = MaterialTheme.typography.bodyMedium) }
            product.note?.let { HorizontalDivider(); Text(it, style = MaterialTheme.typography.bodySmall) }
            product.sourceUrl?.let { Text("Kaynak: $it", style = MaterialTheme.typography.labelSmall) }
            if (product.sourceVerifiedAtEpochMs != null) {
                val formatted = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(product.sourceVerifiedAtEpochMs))
                Text("Kaynak doğrulandı • $formatted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Kaynak doğrulanmadı", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onEdit) { Text("Düzenle") }
                TextButton(onClick = onDelete) { Text("Sil") }
            }
        }
    }
}
