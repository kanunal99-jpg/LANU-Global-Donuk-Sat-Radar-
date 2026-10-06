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
        CrmContactEntity::class,
        CrmActivityEntity::class,
        CrmStageTransitionEntity::class,
        SyncOperationEntity::class,
        CrmNextActionEntity::class,
        CrmOpportunityEntity::class,
        CrmQuoteEntity::class,
        CrmOrderEntity::class,
        CrmQuoteLineEntity::class,
        CrmOrderLineEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
@TypeConverters(CrmRoomConverters::class)
abstract class LanuCrmDatabase : RoomDatabase() {
    abstract fun customerDao(): CrmCustomerDao
    abstract fun contactDao(): CrmContactDao
    abstract fun activityDao(): CrmActivityDao
    abstract fun stageTransitionDao(): CrmStageTransitionDao
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun nextActionDao(): CrmNextActionDao
    abstract fun opportunityDao(): CrmOpportunityDao
    abstract fun quoteDao(): CrmQuoteDao
    abstract fun orderDao(): CrmOrderDao
    abstract fun quoteLineDao(): CrmQuoteLineDao
    abstract fun orderLineDao(): CrmOrderLineDao

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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN dataQuality TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_crm_customer_dataQuality ON crm_customer(dataQuality)",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN contactName TEXT")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN businessType TEXT")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN taxOrNationalId TEXT")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN phone TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN website TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS crm_contact (id TEXT NOT NULL PRIMARY KEY, customerId TEXT NOT NULL, fullName TEXT NOT NULL, role TEXT, phone TEXT, email TEXT, isPrimary INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, version INTEGER NOT NULL, syncState TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_contact_customerId_updatedAtEpochMs ON crm_contact(customerId, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_contact_syncState ON crm_contact(syncState)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS crm_quote (id TEXT NOT NULL PRIMARY KEY, customerId TEXT NOT NULL, opportunityId TEXT, quoteNumber TEXT NOT NULL, status TEXT NOT NULL, currency TEXT NOT NULL, totalMinor INTEGER NOT NULL, validUntilEpochMs INTEGER, notes TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, version INTEGER NOT NULL, syncState TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_quote_customerId_updatedAtEpochMs ON crm_quote(customerId, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_quote_status ON crm_quote(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_quote_syncState ON crm_quote(syncState)")
                db.execSQL("CREATE TABLE IF NOT EXISTS crm_order (id TEXT NOT NULL PRIMARY KEY, customerId TEXT NOT NULL, quoteId TEXT, orderNumber TEXT NOT NULL, status TEXT NOT NULL, currency TEXT NOT NULL, totalMinor INTEGER NOT NULL, notes TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, version INTEGER NOT NULL, syncState TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_customerId_updatedAtEpochMs ON crm_order(customerId, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_quoteId ON crm_order(quoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_status ON crm_order(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_syncState ON crm_order(syncState)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS crm_quote_line (id TEXT NOT NULL PRIMARY KEY, quoteId TEXT NOT NULL, productId TEXT, productName TEXT NOT NULL, unit TEXT NOT NULL, quantityMilli INTEGER NOT NULL, unitPriceMinor INTEGER NOT NULL, discountBasisPoints INTEGER NOT NULL, lineTotalMinor INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, version INTEGER NOT NULL, syncState TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_quote_line_quoteId_updatedAtEpochMs ON crm_quote_line(quoteId, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_quote_line_productId ON crm_quote_line(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_quote_line_syncState ON crm_quote_line(syncState)")
                db.execSQL("CREATE TABLE IF NOT EXISTS crm_order_line (id TEXT NOT NULL PRIMARY KEY, orderId TEXT NOT NULL, productId TEXT, productName TEXT NOT NULL, unit TEXT NOT NULL, quantityMilli INTEGER NOT NULL, unitPriceMinor INTEGER NOT NULL, discountBasisPoints INTEGER NOT NULL, lineTotalMinor INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, version INTEGER NOT NULL, syncState TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_line_orderId_updatedAtEpochMs ON crm_order_line(orderId, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_line_productId ON crm_order_line(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_crm_order_line_syncState ON crm_order_line(syncState)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN signboardName TEXT")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN registryStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN registrySource TEXT")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN registryNumber TEXT")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE crm_customer ADD COLUMN mergedIntoCustomerId TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_crm_customer_mergedIntoCustomerId " +
                        "ON crm_customer(mergedIntoCustomerId)",
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
