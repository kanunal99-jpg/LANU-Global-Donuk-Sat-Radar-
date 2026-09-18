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
        CrmNextActionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(CrmRoomConverters::class)
abstract class LanuCrmDatabase : RoomDatabase() {
    abstract fun customerDao(): CrmCustomerDao
    abstract fun activityDao(): CrmActivityDao
    abstract fun stageTransitionDao(): CrmStageTransitionDao
    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS crm_next_action (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "customerId TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "dueAtEpochMs INTEGER NOT NULL, " +
                        "note TEXT, " +
                        "createdByUserId TEXT, " +
                        "createdAtEpochMs INTEGER NOT NULL, " +
                        "completedAtEpochMs INTEGER, " +
                        "completedByUserId TEXT, " +
                        "version INTEGER NOT NULL, " +
                        "syncState TEXT NOT NULL" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_next_action_customerId_dueAtEpochMs ON crm_next_action(customerId, dueAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_next_action_dueAtEpochMs_completedAtEpochMs ON crm_next_action(dueAtEpochMs, completedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_next_action_syncState ON crm_next_action(syncState)")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE crm_sync_operation " +
                        "ADD COLUMN state TEXT NOT NULL DEFAULT 'PENDING'",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_crm_sync_operation_state_createdAtEpochMs " +
                        "ON crm_sync_operation(state, createdAtEpochMs)",
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
