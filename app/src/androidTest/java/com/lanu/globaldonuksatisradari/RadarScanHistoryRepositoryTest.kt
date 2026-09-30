package com.lanu.globaldonuksatisradari

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarScanHistoryRepositoryTest {
    private lateinit var repository: RadarScanHistoryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = RadarScanHistoryRepository(context)
        repository.clear()
    }

    @Test
    fun secondScanReportsOnlyPreviouslyUnseenBusinesses() {
        val first = repository.compareAndRecord(
            city = "İstanbul",
            district = "Kadıköy",
            query = "kafe",
            records = listOf(business("1"), business("2")),
            nowEpochMs = 1L,
        )
        assertTrue(first.isFirstScan)
        assertEquals(0, first.newCount)

        val second = repository.compareAndRecord(
            city = "İstanbul",
            district = "Kadıköy",
            query = "kafe",
            records = listOf(business("1"), business("2"), business("3")),
            nowEpochMs = 2L,
        )
        assertFalse(second.isFirstScan)
        assertEquals(1, second.newCount)
        assertTrue(second.newBusinessKeys.contains(RadarScanHistoryRepository.businessKey(business("3"))))
    }

    @Test
    fun differentDistrictUsesIndependentBaseline() {
        repository.compareAndRecord("İstanbul", "Kadıköy", "", listOf(business("1")), 1L)
        val other = repository.compareAndRecord("İstanbul", "Beşiktaş", "", listOf(business("2")), 2L)
        assertTrue(other.isFirstScan)
        assertEquals(0, other.newCount)
    }

    private fun business(id: String) = VerifiedBusiness(
        id = id,
        name = "İşletme $id",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = "Caferağa",
        source = DataSourceDescriptor(
            id = "osm-overpass",
            name = "OpenStreetMap Overpass",
            publisher = "OpenStreetMap",
            licenseOrTerms = "ODbL",
            sourceUrl = "https://overpass-api.de/api/interpreter",
            lastVerifiedAtEpochMs = 1L,
        ),
        verifiedAtEpochMs = 1L,
    )
}
