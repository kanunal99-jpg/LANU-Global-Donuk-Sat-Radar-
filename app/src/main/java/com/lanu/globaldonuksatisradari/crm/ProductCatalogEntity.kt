package com.lanu.globaldonuksatisradari.crm
import androidx.room.Entity
import androidx.room.Index
@Entity(tableName="product_catalog",indices=[Index(value=["name"]),Index(value=["sku"]),Index(value=["updatedAtEpochMs"])])
data class ProductCatalogEntity(@androidx.room.PrimaryKey val id:String,val name:String,val sku:String?,val category:String?,val weightGrams:Int?,val packageQuantity:Int?,val unit:String,val priceMinor:Long,val currency:String,val notes:String?,val createdAtEpochMs:Long,val updatedAtEpochMs:Long)
