package com.lanu.globaldonuksatisradari.crm
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao interface ProductCatalogDao{
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(product:ProductCatalogEntity)
 @Query("SELECT * FROM product_catalog ORDER BY updatedAtEpochMs DESC") fun observeAll():Flow<List<ProductCatalogEntity>>
 @Query("SELECT * FROM product_catalog WHERE id=:id LIMIT 1") suspend fun findById(id:String):ProductCatalogEntity?
 @Delete suspend fun delete(product:ProductCatalogEntity)
}
