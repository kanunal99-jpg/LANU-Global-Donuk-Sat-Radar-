package com.lanu.globaldonuksatisradari.crm
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
data class ProductCatalogItem(val id:String,val name:String,val sku:String?,val category:String?,val weightGrams:Int?,val packageQuantity:Int?,val unit:String,val priceMinor:Long,val currency:String,val notes:String?,val createdAtEpochMs:Long,val updatedAtEpochMs:Long)
class ProductCatalogRepository(private val database:LanuCrmDatabase,private val now:()->Long={System.currentTimeMillis()},private val idGenerator:()->String={UUID.randomUUID().toString()}){
 fun observeProducts():Flow<List<ProductCatalogItem>>=database.productCatalogDao().observeAll().map{it.map(::toDomain)}
 suspend fun addProduct(name:String,sku:String?,category:String?,weightGrams:Int?,packageQuantity:Int?,unit:String,priceMinor:Long,currency:String,notes:String?):ProductCatalogItem=database.withTransaction{
  require(name.trim().isNotEmpty()){"Ürün adı boş olamaz."}; require(unit.trim().isNotEmpty()){"Birim boş olamaz."}; require(priceMinor>=0){"Fiyat negatif olamaz."}; require(currency.trim().isNotEmpty()){"Para birimi boş olamaz."}; require(weightGrams==null||weightGrams>0){"Gramaj 0'dan büyük olmalıdır."}; require(packageQuantity==null||packageQuantity>0){"Koli/adet 0'dan büyük olmalıdır."}
  val t=now(); val item=ProductCatalogItem(idGenerator(),name.trim(),sku?.trim()?.takeIf{it.isNotEmpty()},category?.trim()?.takeIf{it.isNotEmpty()},weightGrams,packageQuantity,unit.trim(),priceMinor,currency.trim().uppercase(),notes?.trim()?.takeIf{it.isNotEmpty()},t,t)
  database.productCatalogDao().upsert(toEntity(item)); item
 }
 suspend fun deleteProduct(id:String){database.productCatalogDao().findById(id)?.let{database.productCatalogDao().delete(it)}}
 private fun toEntity(i:ProductCatalogItem)=ProductCatalogEntity(i.id,i.name,i.sku,i.category,i.weightGrams,i.packageQuantity,i.unit,i.priceMinor,i.currency,i.notes,i.createdAtEpochMs,i.updatedAtEpochMs)
 private fun toDomain(e:ProductCatalogEntity)=ProductCatalogItem(e.id,e.name,e.sku,e.category,e.weightGrams,e.packageQuantity,e.unit,e.priceMinor,e.currency,e.notes,e.createdAtEpochMs,e.updatedAtEpochMs)
}
