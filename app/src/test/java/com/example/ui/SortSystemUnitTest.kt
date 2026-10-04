package com.example.ui

import com.example.data.local.entity.ActorEntity
import com.example.data.local.entity.LinkEntity
import com.example.data.local.entity.StudioEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SortSystemUnitTest {

    @Test
    fun testSortModeEnumsExactOptions() {
        val options = SortMode.values()
        assertEquals(4, options.size)
        assertEquals(SortMode.NEW, options[0])
        assertEquals(SortMode.OLD, options[1])
        assertEquals(SortMode.RECENTLY_ADDED, options[2])
        assertEquals(SortMode.OLDEST_ADDED, options[3])
    }

    @Test
    fun testManagementSortOptionEnumsExactOptions() {
        val options = ManagementSortOption.values()
        assertEquals(4, options.size)
        assertEquals(ManagementSortOption.NAME_AZ, options[0])
        assertEquals(ManagementSortOption.NAME_ZA, options[1])
        assertEquals(ManagementSortOption.RECENTLY_ADDED, options[2])
        assertEquals(ManagementSortOption.OLDEST_ADDED, options[3])
    }

    @Test
    fun testSceneSortingLogic() {
        val link1 = LinkEntity(
            id = "1",
            title = "Alpha",
            assignedDate = 1000L,
            createdAt = 5000L
        )
        val link2 = LinkEntity(
            id = "2",
            title = "Beta",
            assignedDate = 3000L,
            createdAt = 2000L
        )
        val link3 = LinkEntity(
            id = "3",
            title = "Gamma",
            assignedDate = 2000L,
            createdAt = 8000L
        )

        val list = listOf(link1, link2, link3)

        // NEW: By assignedDate descending
        val newSorted = list.sortedByDescending { it.assignedDate ?: it.createdAt }
        assertEquals(listOf("2", "3", "1"), newSorted.map { it.id })

        // OLD: By assignedDate ascending
        val oldSorted = list.sortedBy { it.assignedDate ?: it.createdAt }
        assertEquals(listOf("1", "3", "2"), oldSorted.map { it.id })

        // RECENTLY_ADDED: Purely by createdAt descending (not modified)
        val recentSorted = list.sortedByDescending { it.createdAt }
        assertEquals(listOf("3", "1", "2"), recentSorted.map { it.id })

        // OLDEST_ADDED: Purely by createdAt ascending
        val oldestSorted = list.sortedBy { it.createdAt }
        assertEquals(listOf("2", "1", "3"), oldestSorted.map { it.id })
    }

    @Test
    fun testActorManagementSortingLogic() {
        val a1 = ActorEntity(id = "1", name = "Zara", createdAt = 1000L)
        val a2 = ActorEntity(id = "2", name = "Alex", createdAt = 5000L)
        val a3 = ActorEntity(id = "3", name = "Bella", createdAt = 3000L)

        val actors = listOf(a1, a2, a3)

        // A-Z
        val az = actors.sortedBy { it.name.lowercase() }
        assertEquals(listOf("Alex", "Bella", "Zara"), az.map { it.name })

        // Z-A
        val za = actors.sortedByDescending { it.name.lowercase() }
        assertEquals(listOf("Zara", "Bella", "Alex"), za.map { it.name })

        // Recently Added
        val recent = actors.sortedByDescending { it.createdAt }
        assertEquals(listOf("2", "3", "1"), recent.map { it.id })

        // Oldest Added
        val oldest = actors.sortedBy { it.createdAt }
        assertEquals(listOf("1", "3", "2"), oldest.map { it.id })
    }

    @Test
    fun testStudioManagementSortingLogic() {
        val s1 = StudioEntity(id = "1", name = "Wicked", createdAt = 2000L)
        val s2 = StudioEntity(id = "2", name = "Brazzers", createdAt = 7000L)
        val s3 = StudioEntity(id = "3", name = "Naughty", createdAt = 1000L)

        val studios = listOf(s1, s2, s3)

        // A-Z
        val az = studios.sortedBy { it.name.lowercase() }
        assertEquals(listOf("Brazzers", "Naughty", "Wicked"), az.map { it.name })

        // Z-A
        val za = studios.sortedByDescending { it.name.lowercase() }
        assertEquals(listOf("Wicked", "Naughty", "Brazzers"), za.map { it.name })

        // Recently Added
        val recent = studios.sortedByDescending { it.createdAt }
        assertEquals(listOf("2", "1", "3"), recent.map { it.id })

        // Oldest Added
        val oldest = studios.sortedBy { it.createdAt }
        assertEquals(listOf("3", "1", "2"), oldest.map { it.id })
    }
}
