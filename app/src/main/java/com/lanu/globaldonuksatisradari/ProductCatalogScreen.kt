package com.lanu.globaldonuksatisradari

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ProductCatalogScreen(repository: ProductCatalogRepository) {
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
    var sourceUrl by remember { mutableStateOf("https://globaldonukgida.com/") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var cameraTarget by remember { mutableStateOf<CameraImageTarget?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            editorError = runCatching {
                val persisted = withContext(Dispatchers.IO) {
                    ProductMediaStore.persistGalleryImage(context, uri)
                }
                imageUrl = persisted
            }.exceptionOrNull()?.message
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val target = cameraTarget
        cameraTarget = null
        if (target == null) return@rememberLauncherForActivityResult
        if (!success) {
            runCatching { target.file.delete() }
            return@rememberLauncherForActivityResult
        }
        editorError = runCatching {
            imageUrl = ProductMediaStore.finalizeCameraImage(context, target)
        }.exceptionOrNull()?.message
    }

    fun openNew() {
        editingId = null
        name = ""
        category = ""
        unit = "Adet"
        price = ""
        currency = "TRY"
        note = ""
        description = ""
        imageUrl = ""
        sourceUrl = "https://globaldonukgida.com/"
        editorError = null
        editorOpen = true
    }

    fun openEdit(product: CatalogProduct) {
        editingId = product.id
        name = product.name
        category = product.category
        unit = product.unit
        price = (product.priceMinor / 100.0).toString().replace(".", ",")
        currency = product.currency
        note = product.note.orEmpty()
        description = product.description.orEmpty()
        imageUrl = product.imageUrl.orEmpty()
        sourceUrl = product.sourceUrl ?: "https://globaldonukgida.com/"
        editorError = null
        editorOpen = true
    }

    fun save() {
        editorError = runCatching {
            val normalizedSourceUrl = sourceUrl.trim().takeIf { it.isNotEmpty() }
            val existingProduct = editingId?.let { id -> products.firstOrNull { it.id == id } }
            val preservedVerification = existingProduct
                ?.sourceVerifiedAtEpochMs
                ?.takeIf { existingProduct.sourceUrl == normalizedSourceUrl }
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
                sourceVerifiedAtEpochMs = preservedVerification,
            )
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
                    Text(
                        if (products.isEmpty()) "Ürün kataloğu henüz yüklenmedi" else "Eşleşen ürün bulunamadı",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (products.isEmpty()) {
                            "Yetkili SKU/fiyat kaynağı olmadan ürün veya fiyat uydurulmaz. " +
                                "Ürünü manuel kaydedebilirsiniz; kaynak doğrulaması ayrıca yapılmalıdır."
                        } else {
                            "Arama metnini değiştirerek tekrar deneyin."
                        },
                    )
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
            onDismissRequest = { editorOpen = false },
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
                    if (imageUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Ürün görseli önizleme",
                                modifier = Modifier.fillMaxWidth().height(180.dp).testTag("product_image_preview"),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            if (imageUrl.startsWith("https://")) "Görsel kaynağı: URL"
                            else "Görsel kaynağı: Galeri/Kamera",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = imageUrl.takeIf { it.startsWith("https://") }.orEmpty(),
                        onValueChange = { imageUrl = it.trim() },
                        modifier = Modifier.fillMaxWidth().testTag("product_image_url_input"),
                        singleLine = true,
                        label = { Text("Ürün fotoğrafı HTTPS URL") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).testTag("product_image_gallery"),
                        ) {
                            Text("Galeriden seç")
                        }
                        OutlinedButton(
                            onClick = {
                                editorError = runCatching {
                                    ProductMediaStore.createCameraTarget(context)
                                }.fold(
                                    onSuccess = { target ->
                                        cameraTarget = target
                                        cameraLauncher.launch(target.uri)
                                        null
                                    },
                                    onFailure = { it.message },
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("product_image_camera"),
                        ) {
                            Text("Fotoğraf çek")
                        }
                    }
                    if (imageUrl.isNotBlank()) {
                        TextButton(
                            onClick = { imageUrl = "" },
                            modifier = Modifier.fillMaxWidth().testTag("product_image_remove"),
                        ) {
                            Text("Görseli kaldır")
                        }
                    }
                    OutlinedTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Kaynak URL (opsiyonel)") })
                    OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Not") })
                    editorError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { Button(onClick = ::save, modifier = Modifier.testTag("product_save_button")) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("Vazgeç") } },
        )
    }

    val deletingProduct = deletingId?.let { id -> products.firstOrNull { it.id == id } }
    if (deletingProduct != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Ürünü sil") },
            text = { Text("“${deletingProduct.name}” kaydı katalogdan silinsin mi?") },
            confirmButton = { Button(onClick = { repository.delete(deletingProduct.id); deletingId = null }) { Text("Sil") } },
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
            product.description?.let { HorizontalDivider(); Text(it, style = MaterialTheme.typography.bodyMedium) }
            product.note?.let { HorizontalDivider(); Text(it, style = MaterialTheme.typography.bodySmall) }
            product.sourceUrl?.let { Text("Kaynak URL: $it", style = MaterialTheme.typography.labelSmall) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onEdit) { Text("Düzenle") }
                TextButton(onClick = onDelete) { Text("Sil") }
            }
        }
    }
}
