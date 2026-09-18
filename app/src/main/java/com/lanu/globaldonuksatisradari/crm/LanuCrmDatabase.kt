package com.lanu.globaldonuksatisradari.crm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CrmCustomerEntity::class,
        CrmActivityEntity::class,
        CrmStageTransitionEntity::class,
        SyncOperationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(CrmRoomConverters::class)
abstract class LanuCrmDatabase : RoomDatabase() {
    abstract fun customerDao(): CrmCustomerDao
    abstract fun activityDao(): CrmActivityDao
    abstract fun stageTransitionDao(): CrmStageTransitionDao
    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE crm_sync_operation " +
                        "ADD COLUMN state TEXT NOT NULL DEFAULT 'PENDING'",
                )
            }
        }

        @Volatile
        private var instance: LanuCrmDatabase? = null

        fun getInstance(context: Context): LanuCrmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LanuCrmDatabase::class.java,
                    "lanu_global_donuk_crm.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
