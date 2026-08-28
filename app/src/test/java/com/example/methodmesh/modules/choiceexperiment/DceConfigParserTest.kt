package com.example.methodmesh.modules.choiceexperiment

import org.junit.Assert.assertEquals
import org.junit.Test

class DceConfigParserTest {
    @Test
    fun `pairwise reads rounds and item list`() {
        val config = DceConfigParser.from(
            mapOf("rounds" to "7", "items" to "Panasonic|Sony|Nintendo", "seed" to "test"),
            DceMethod.Pairwise
        )
        assertEquals(7, config.rounds)
        assertEquals(listOf("Panasonic", "Sony", "Nintendo"), config.options)
        assertEquals(2, config.optionsPerRound)
    }

    @Test
    fun `maxdiff reads rounds and item list`() {
        val config = DceConfigParser.from(
            mapOf("rounds" to "6", "items" to "Speed|Price|Quality|Service", "seed" to "test"),
            DceMethod.MaxDiff
        )
        assertEquals(6, config.rounds)
        assertEquals(4, config.itemsPerRound)
        assertEquals(listOf("Speed", "Price", "Quality", "Service"), config.options)
    }

    @Test
    fun `ranking ranks the full supplied item list for each round`() {
        val config = DceConfigParser.from(
            mapOf("rounds" to "4", "items" to "A|B|C|D|E", "seed" to "test"),
            DceMethod.Ranking
        )
        assertEquals(4, config.rounds)
        assertEquals(5, config.optionsPerRound)
    }

    @Test
    fun `points reads budget and item list`() {
        val config = DceConfigParser.from(
            mapOf("points" to "100", "items" to "A|B|C", "seed" to "test"),
            DceMethod.Points
        )
        assertEquals(100, config.totalPoints)
        assertEquals(listOf("A", "B", "C"), config.options)
    }

    @Test
    fun `conjoint reads named classes and their options`() {
        val config = DceConfigParser.from(
            mapOf(
                "rounds" to "5",
                "classes" to "BRAND:Panasonic,Sony,Nintendo|FEATURE:Basic,Premium|PRICE:Low,High",
                "seed" to "test"
            ),
            DceMethod.Conjoint
        )
        assertEquals(5, config.rounds)
        assertEquals(listOf("Panasonic", "Sony", "Nintendo"), config.attributes["BRAND"])
        assertEquals(listOf("Basic", "Premium"), config.attributes["FEATURE"])
        assertEquals(listOf("Low", "High"), config.attributes["PRICE"])
    }

    @Test
    fun `ODK multiline item extras are parsed without transport delimiters`() {
        val config = DceConfigParser.from(
            mapOf("items" to "Clinic A\nClinic B\nClinic C", "rounds" to "3"),
            DceMethod.Ranking
        )

        assertEquals(listOf("Clinic A", "Clinic B", "Clinic C"), config.options)
    }

    @Test
    fun `ODK multiline conjoint classes preserve colon and comma data`() {
        val config = DceConfigParser.from(
            mapOf(
                "classes" to "BRAND: Panasonic, Sony\nFEATURE: Basic, Premium\nPRICE: 100, 200"
            ),
            DceMethod.Conjoint
        )

        assertEquals(listOf("Panasonic", "Sony"), config.attributes["BRAND"])
        assertEquals(listOf("Basic", "Premium"), config.attributes["FEATURE"])
        assertEquals(listOf("100", "200"), config.attributes["PRICE"])
    }
}
