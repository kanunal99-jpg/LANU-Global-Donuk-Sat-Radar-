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
        CrmOpportunityEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(CrmRoomConverters::class)
abstract class LanuCrmDatabase : RoomDatabase() {
    abstract fun customerDao(): CrmCustomerDao
    abstract fun activityDao(): CrmActivityDao
    abstract fun stageTransitionDao(): CrmStageTransitionDao
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun nextActionDao(): CrmNextActionDao
    abstract fun opportunityDao(): CrmOpportunityDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS crm_opportunity (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "customerId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "notes TEXT, " +
                        "estimatedValueMinor INTEGER, " +
                        "currency TEXT, " +
                        "valueOrigin TEXT NOT NULL, " +
                        "createdAtEpochMs INTEGER NOT NULL, " +
                        "updatedAtEpochMs INTEGER NOT NULL, " +
                        "version INTEGER NOT NULL, " +
                        "syncState TEXT NOT NULL" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_opportunity_customerId_updatedAtEpochMs ON crm_opportunity(customerId, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_opportunity_status ON crm_opportunity(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_opportunity_syncState ON crm_opportunity(syncState)")
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN address TEXT")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN longitude REAL")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_crm_customer_latitude_longitude " +
                        "ON crm_customer(latitude, longitude)",
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
