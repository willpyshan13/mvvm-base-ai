package com.will.mvvmbase.core.apm

import org.junit.Assert.assertEquals
import org.junit.Test

class ApmSqlDiagnosticsTest {
    @Test
    fun flagsCommonExpensiveOrRiskySqlPatterns() {
        assertEquals(setOf("select_star"), ApmSqlDiagnostics.findIssues("SELECT * FROM tasks"))
        assertEquals(setOf("update_without_where"), ApmSqlDiagnostics.findIssues("UPDATE tasks SET done = 1"))
        assertEquals(setOf("delete_without_where"), ApmSqlDiagnostics.findIssues("DELETE FROM tasks"))
        assertEquals(setOf("autoincrement"), ApmSqlDiagnostics.findIssues("CREATE TABLE x (id INTEGER PRIMARY KEY AUTOINCREMENT)"))
        assertEquals(setOf("insert_or_replace"), ApmSqlDiagnostics.findIssues("INSERT OR REPLACE INTO x VALUES (?)"))
        assertEquals(setOf("leading_wildcard_like"), ApmSqlDiagnostics.findIssues("SELECT id FROM x WHERE name LIKE ?", listOf("%term")))
    }

    @Test
    fun ignoresCommentsAndSafeQueries() {
        assertEquals(emptySet<String>(), ApmSqlDiagnostics.findIssues("SELECT id FROM tasks WHERE id = ?", listOf(4)))
        assertEquals(emptySet<String>(), ApmSqlDiagnostics.findIssues("-- SELECT * FROM ignored\nSELECT id FROM tasks"))
    }

    @Test
    fun detectsDistinctSelectStarReplaceAndLiteralPredicates() {
        assertEquals(
            setOf("select_star", "literal_predicate"),
            ApmSqlDiagnostics.findIssues("SELECT DISTINCT * FROM x WHERE age >= 18"),
        )
        assertEquals(setOf("insert_or_replace"), ApmSqlDiagnostics.findIssues("REPLACE INTO x VALUES (?)"))
    }
}
