package com.lanu.globaldonuksatisradari.crm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.OfficialRegistryEvidence
import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import com.lanu.globaldonuksatisradari.data.SupplementalBusinessDirectory
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import com.lanu.globaldonuksatisradari.data.VerifiedBusinessValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class CrmRoomInstrumentationTest {

    @Test
    fun customerSave_persistsCustomerAndSyncOperation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LanuCrmDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val business = VerifiedBusiness(
                id = "osm-node-123",
                name = "Smoke Test Kafe",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                source = DataSourceDescriptor(
                    id = "osm-nominatim",
                    name = "OpenStreetMap Nominatim",
                    publisher = "OpenStreetMap",
                    licenseOrTerms = "ODbL",
                    sourceUrl = "https://nominatim.openstreetmap.org/",
                    lastVerifiedAtEpochMs = 1L,
                ),
                verifiedAtEpochMs = 1L,
                latitude = 40.99,
                longitude = 29.03,
                category = "cafe",
                phone = "05550000000",
                website = "https://smoke.example",
                officialRegistryEvidence = OfficialRegistryEvidence(
                    source = OfficialRegistrySource.ITO.descriptor,
                    registrationNumber = "SICIL-123",
                    status = "Faal",
                    importedAtEpochMs = 900L,
                    fieldsUsed = setOf("status", "phone", "website", "tax_or_national_id"),
                    taxOrNationalId = "1234567890",
                ),
            )
            assertTrue(VerifiedBusinessValidator.validate(business).isSuccess)

            var idIndex = 0
            val repository = LocalCrmRepository(
                database = database,
                now = { 1_000L },
                idGenerator = { "test-id-" + (idIndex++) },
            )

            val customer = repository.addBusinessAsCustomer(
                business = business,
                ownerUserId = "instrumentation-test",
            )
            val duplicate = repository.addBusinessAsCustomer(
                business = business,
                ownerUserId = "instrumentation-test",
            )
            assertEquals(customer.id, duplicate.id)
            assertEquals(1, repository.pendingSync().size)

            val observed = repository.observeCustomers("İstanbul").first()
            val pending = repository.pendingSync()
            val transitions = database.stageTransitionDao().observeForCustomer(customer.id).first()
            val action = repository.createNextAction(
                customerId = customer.id,
                type = CrmNextActionType.PROPOSAL_FOLLOW_UP,
                dueAtEpochMs = 2_000L,
                note = "Teklif takibi",
                createdByUserId = "instrumentation-test",
            )
            val actions = repository.observeNextActions(customer.id).first()
            assertEquals(1, actions.size)
            assertEquals(CrmNextActionType.PROPOSAL_FOLLOW_UP, actions.single().type)
            assertEquals(2_000L, actions.single().dueAtEpochMs)
            assertEquals(SyncState.PENDING_UPLOAD, actions.single().syncState)

            val completed = repository.completeNextAction(action.id, "instrumentation-test")
            assertTrue(completed.completedAtEpochMs != null)
            val activitiesAfterCompletion = repository.observeActivities(customer.id).first()
            assertEquals(1, activitiesAfterCompletion.size)
            assertEquals(CrmActivityType.PROPOSAL, activitiesAfterCompletion.single().type)
            assertEquals("Teklif takibi", activitiesAfterCompletion.single().note)
            assertEquals(4, repository.pendingSync().size)

            assertEquals("Smoke Test Kafe", observed.single().businessName)
            assertEquals("Smoke Test Kafe", observed.single().signboardName)
            assertEquals(CrmRegistryStatus.ACTIVE, observed.single().registryStatus)
            assertEquals("İTO Resmî Üye/Firma Kaydı", observed.single().registrySource)
            assertEquals("SICIL-123", observed.single().registryNumber)
            assertEquals("1234567890", observed.single().taxOrNationalId)
            assertEquals(DataQuality.OBSERVED, observed.single().dataQuality)
            assertEquals(SyncState.PENDING_UPLOAD, observed.single().syncState)
            assertEquals(customer.id, pending.single().entityId)
            assertEquals(LocalCrmRepository.OP_CREATE, pending.single().operation)
            val pendingEntity = database.syncOperationDao().pending(100)
                .single {
                    it.entityType == LocalCrmRepository.ENTITY_CUSTOMER &&
                        it.entityId == customer.id
                }
            val pendingPayload = JSONObject(pendingEntity.payloadJson)
            assertEquals("05550000000", pendingPayload.getString("phone"))
            assertEquals("https://smoke.example", pendingPayload.getString("website"))
            assertEquals("Smoke Test Kafe", pendingPayload.getString("signboardName"))
            assertEquals("ACTIVE", pendingPayload.getString("registryStatus"))
            assertEquals("İTO Resmî Üye/Firma Kaydı", pendingPayload.getString("registrySource"))
            assertEquals("SICIL-123", pendingPayload.getString("registryNumber"))
            assertEquals("1234567890", pendingPayload.getString("taxOrNationalId"))
            assertEquals("https://smoke.example", observed.single().website)
            assertEquals(1, transitions.size)
            assertEquals(CrmStage.PROSPECT.name, transitions.single().toStage)

            val opportunity = repository.createOpportunity(
                customerId = customer.id,
                title = "Bahar menü fırsatı",
                notes = "Müşteri kendi tahminini paylaştı.",
                estimatedValueMinor = 250_000L,
                currency = "TRY",
                valueOrigin = CrmValueOrigin.USER_ENTERED,
                createdByUserId = "instrumentation-test",
            )
            val opportunities = repository.observeOpportunities(customer.id).first()
            assertEquals(1, opportunities.size)
            assertEquals("Bahar menü fırsatı", opportunities.single().title)
            assertEquals(250_000L, opportunities.single().estimatedValueMinor)
            assertEquals("TRY", opportunities.single().currency)
            assertEquals(CrmValueOrigin.USER_ENTERED, opportunities.single().valueOrigin)
            assertEquals(CrmOpportunityStatus.OPEN, opportunities.single().status)
            assertEquals(SyncState.PENDING_UPLOAD, opportunities.single().syncState)

            val won = repository.transitionOpportunity(opportunity.id, CrmOpportunityStatus.WON)
            assertEquals(CrmOpportunityStatus.WON, won.status)
            assertEquals(6, repository.pendingSync().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun inactiveRegistryEvidence_marksOnlyTargetOwnerAndPreservesOperationalFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LanuCrmDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            var idIndex = 0
            val repository = LocalCrmRepository(
                database = database,
                now = { 3_000L },
                idGenerator = { "registry-test-" + (idIndex++) },
            )
            val source = DataSourceDescriptor(
                id = "osm-overpass",
                name = "OpenStreetMap Overpass",
                publisher = "OpenStreetMap",
                licenseOrTerms = "ODbL",
                sourceUrl = "https://www.openstreetmap.org/",
                lastVerifiedAtEpochMs = 1L,
            )
            val business = VerifiedBusiness(
                id = "osm-registry-owner-test",
                name = "Pasif Sicil Test",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                source = source,
                verifiedAtEpochMs = 1L,
                latitude = 40.991,
                longitude = 29.031,
                address = "Güncel Operasyon Adresi",
                phone = "05551112233",
            )

            val ownerA = repository.addBusinessAsCustomer(business, ownerUserId = "owner-a")
            val ownerB = repository.addBusinessAsCustomer(business, ownerUserId = "owner-b")

            val result = repository.enrichCustomersFromOfficialRegistryForOwner(
                records = listOf(
                    OfficialRegistryRecord(
                        source = OfficialRegistrySource.ITO,
                        registrationNumber = "PASIF-99",
                        businessName = "Pasif Sicil Test",
                        status = "Pasif",
                        city = "İstanbul",
                        district = "Kadıköy",
                        neighborhood = "Eski Mahalle",
                        address = "Eski Sicil Adresi",
                        phone = "02160000000",
                        website = "https://eski.example",
                        importedAtEpochMs = 2_000L,
                        taxOrNationalId = "1111111111",
                    ),
                ),
                ownerUserId = "owner-a",
            )

            assertEquals(1, result.matched)
            assertEquals(1, result.inactiveMatches)
            assertEquals(1, result.updated)

            val updatedA = database.customerDao().findById(ownerA.id)!!
            val untouchedB = database.customerDao().findById(ownerB.id)!!

            assertEquals(CrmRegistryStatus.INACTIVE.name, updatedA.registryStatus)
            assertEquals("PASIF-99", updatedA.registryNumber)
            assertEquals("İTO Resmî Üye/Firma Kaydı", updatedA.registrySource)
            assertEquals("1111111111", updatedA.taxOrNationalId)
            assertEquals("Güncel Operasyon Adresi", updatedA.address)
            assertEquals("05551112233", updatedA.phone)
            assertEquals(CrmRegistryStatus.UNVERIFIED.name, untouchedB.registryStatus)
            assertEquals(null, untouchedB.registryNumber)
            assertEquals("Güncel Operasyon Adresi", untouchedB.address)
            assertEquals("05551112233", untouchedB.phone)
        } finally {
            database.close()
        }
    }

    @Test
    fun manualCustomerPoint_persistsAddressCoordinatesAndEntersRoutinePool() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LanuCrmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = LocalCrmRepository(
                database = database,
                now = { 5_000L },
                idGenerator = { "manual-test-id" },
            )
            val customer = repository.addManualCustomerPoint(
                businessName = "Manuel Nokta",
                address = "Örnek Mah. 10/A",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.991,
                longitude = 29.031,
            )
            val observed = repository.observeCustomers("İstanbul").first().single()
            assertEquals("Örnek Mah. 10/A", observed.address)
            assertEquals(40.991, observed.latitude ?: Double.NaN, 0.000001)
            assertEquals(29.031, observed.longitude ?: Double.NaN, 0.000001)
            assertEquals(DataQuality.USER_ENTERED, observed.dataQuality)
            assertEquals(SyncState.LOCAL_ONLY, observed.syncState)
            assertEquals(customer.id, observed.id)
            assertEquals(listOf(customer.id), CrmRoutePlanner.plan(listOf(observed)).map { it.customer.id })
            assertTrue(repository.pendingSync().none { it.entityId == customer.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun sandoraCoverageAcceptance_reachesCrmAndExcel() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LanuCrmDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val business = SupplementalBusinessDirectory(context)
                .search(
                    query = "sandora cafe",
                    city = "İstanbul",
                    district = "Sultanbeyli",
                    neighborhood = "Mimar Sinan",
                )
                .single { it.name == "Sandora Fast Food & Cafe" }

            assertEquals("Sultanbeyli", business.district)
            assertEquals("Mimar Sinan", business.neighborhood)
            assertEquals("+90 537 519 74 53", business.phone)
            assertTrue(business.address.orEmpty().contains("Özgürlük Cd. No:76/A"))

            var idIndex = 0
            val repository = LocalCrmRepository(
                database = database,
                now = { 10_000L },
                idGenerator = { "sandora-test-" + (idIndex++) },
            )
            val saved = repository.addBusinessAsCustomer(
                business = business,
                ownerUserId = null,
            )

            assertEquals("Sandora Fast Food & Cafe", saved.businessName)
            assertEquals("Sandora Fast Food & Cafe", saved.signboardName)
            assertEquals("Sultanbeyli", saved.district)
            assertEquals("Mimar Sinan", saved.neighborhood)
            assertEquals("+90 537 519 74 53", saved.phone)
            assertEquals(CrmRegistryStatus.UNVERIFIED, saved.registryStatus)

            val persisted = repository.observeCustomers("İstanbul")
                .first()
                .single { it.businessName == "Sandora Fast Food & Cafe" }
            val workbook = CrmExcelExporter.build(listOf(persisted))

            var sheetXml = ""
            ZipInputStream(ByteArrayInputStream(workbook)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "xl/worksheets/sheet1.xml") {
                        sheetXml = zip.bufferedReader(Charsets.UTF_8).readText()
                        break
                    }
                }
            }

            assertTrue(sheetXml.contains("Sandora Fast Food &amp; Cafe"))
            assertTrue(sheetXml.contains("Sultanbeyli"))
            assertTrue(sheetXml.contains("Mimar Sinan"))
            assertTrue(sheetXml.contains("+90 537 519 74 53"))
            assertTrue(sheetXml.contains("DOĞRULANMADI"))
        } finally {
            database.close()
        }
    }

}
