package com.lanu.globaldonuksatisradari.crm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CrmCustomerEntity::class,
        CrmActivityEntity::class,
        CrmStageTransitionEntity::class,
        SyncOperationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CrmRoomConverters::class)
abstract class LanuCrmDatabase : RoomDatabase() {
    abstract fun customerDao(): CrmCustomerDao
    abstract fun activityDao(): CrmActivityDao
    abstract fun stageTransitionDao(): CrmStageTransitionDao
    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        @Volatile
        private var instance: LanuCrmDatabase? = null

        fun getInstance(context: Context): LanuCrmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LanuCrmDatabase::class.java,
                    "lanu_global_donuk_crm.db",
                ).build().also { instance = it }
            }
    }
}
