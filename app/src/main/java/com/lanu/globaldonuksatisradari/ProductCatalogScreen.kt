package com.lanu.globaldonuksatisradari
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.*
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.util.Locale
@Composable fun ProductCatalogScreen(repository:ProductCatalogRepository){
 val products by repository.observeProducts().collectAsState(initial=emptyList()); val scope=rememberCoroutineScope()
 var name by remember{mutableStateOf("")}; var sku by remember{mutableStateOf("")}; var category by remember{mutableStateOf("")}; var weight by remember{mutableStateOf("")}; var packageQuantity by remember{mutableStateOf("")}; var unit by remember{mutableStateOf("adet")}; var price by remember{mutableStateOf("")}; var currency by remember{mutableStateOf("TRY")}; var notes by remember{mutableStateOf("")}; var error by remember{mutableStateOf<String?>(null)}; var message by remember{mutableStateOf<String?>(null)}
 fun parsePriceMinor(value:String):Long{val d=value.trim().replace(",","." ).toBigDecimalOrNull()?:error("Geçerli bir fiyat girin.");require(d>=java.math.BigDecimal.ZERO){"Fiyat negatif olamaz."};return d.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()}
 fun clear(){name="";sku="";category="";weight="";packageQuantity="";unit="adet";price="";currency="TRY";notes=""}
 LazyColumn(modifier=Modifier.fillMaxWidth().padding(16.dp).testTag("product_catalog_scroll"),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("Manuel Ürün Kataloğu",style=MaterialTheme.typography.headlineSmall);Text("Ürünleri ve kullanıcı tarafından girilen fiyatlarını kaydedin.")}
  item{OutlinedTextField(name,{name=it},Modifier.fillMaxWidth().testTag("product_name"),label={Text("Ürün adı *")},singleLine=true)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){OutlinedTextField(sku,{sku=it},Modifier.weight(1f),label={Text("SKU / kod")},singleLine=true);OutlinedTextField(category,{category=it},Modifier.weight(1f),label={Text("Kategori")},singleLine=true)}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){OutlinedTextField(weight,{weight=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Gramaj")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);OutlinedTextField(packageQuantity,{packageQuantity=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Koli / adet")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);OutlinedTextField(unit,{unit=it},Modifier.weight(1f),label={Text("Birim *")},singleLine=true)}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){OutlinedTextField(price,{price=it},Modifier.weight(2f).testTag("product_price"),label={Text("Fiyat *")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);OutlinedTextField(currency,{currency=it},Modifier.weight(1f),label={Text("Para birimi *")},singleLine=true)}}
  item{OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("Not")},minLines=2)}
  item{Button(onClick={error=null;message=null;scope.launch{runCatching{repository.addProduct(name,sku,category,weight.toIntOrNull(),packageQuantity.toIntOrNull(),unit,parsePriceMinor(price),currency,notes)}.onSuccess{clear();message="Ürün kataloğa kaydedildi."}.onFailure{error=it.message?:"Ürün kaydedilemedi."}}},modifier=Modifier.fillMaxWidth().testTag("product_save")){Text("Ürünü kaydet")}}
  error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}};message?.let{item{Text(it)}};item{Text("Kayıtlı ürünler (${products.size})",style=MaterialTheme.typography.titleMedium)}
  items(products,key={it.id}){p->ProductCatalogCard(p){scope.launch{repository.deleteProduct(p.id);message="Ürün silindi."}}}
 }}
@Composable private fun ProductCatalogCard(product:ProductCatalogItem,onDelete:()->Unit){
 val price=String.format(Locale.US,"%.2f %s",product.priceMinor/100.0,product.currency)
 Card(modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(product.name,style=MaterialTheme.typography.titleMedium);product.sku?.let{Text("SKU: $it",style=MaterialTheme.typography.bodySmall)};product.category?.let{Text("Kategori: $it",style=MaterialTheme.typography.bodySmall)};Text(buildList{product.weightGrams?.let{add("$it g")};product.packageQuantity?.let{add("$it koli/adet")};add(product.unit)}.joinToString(" • "));Text("Fiyat: $price");product.notes?.let{Text("Not: $it",style=MaterialTheme.typography.bodySmall)};Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){OutlinedButton(onClick=onDelete,modifier=Modifier.testTag("product_delete_${product.id}")){Text("Sil")}}}}
}
