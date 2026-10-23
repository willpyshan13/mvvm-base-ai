package com.will.mvvmbase.core.apm

import androidx.room.RoomDatabase
import java.util.concurrent.Executors

/** Lightweight runtime checks for Room SQL. They intentionally report rule IDs, never SQL or values. */
internal object ApmSqlDiagnostics {
    private val callbackExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "app-apm-sql-diagnostics").apply { isDaemon = true }
    }
    private val selectStar = Regex("\\bSELECT\\s+(?:DISTINCT\\s+)?\\*(?:\\s|;|$)", RegexOption.IGNORE_CASE)
    private val updateWithoutWhere = Regex("\\bUPDATE\\b[\\s\\S]*?\\bSET\\b[\\s\\S]*?;?\\s*$", RegexOption.IGNORE_CASE)
    private val deleteWithoutWhere = Regex("\\bDELETE\\s+FROM\\b[\\s\\S]*;?\\s*$", RegexOption.IGNORE_CASE)
    private val leadingWildcard = Regex("\\bLIKE\\s+['\"]%", RegexOption.IGNORE_CASE)
    private val likeParameter = Regex("\\bLIKE\\s+\\?", RegexOption.IGNORE_CASE)
    private val literalPredicate = Regex("\\bWHERE\\b[\\s\\S]*?(?:=|<>|!=|>=|<=|>|<)\\s*(?:'[^']*'|\\\"[^\\\"]*\\\"|\\d+)(?:\\s|$)", RegexOption.IGNORE_CASE)
    private val replaceConflict = Regex("\\bINSERT\\s+OR\\s+REPLACE\\b|\\bREPLACE\\s+INTO\\b", RegexOption.IGNORE_CASE)

    fun queryCallback(): RoomDatabase.QueryCallback = RoomDatabase.QueryCallback { sql, bindArgs ->
        AppApm.incrementCounter("app.sqlite.query.count")
        findIssues(sql, bindArgs).forEach { rule ->
            AppApm.incrementCounter("app.sqlite.issue.count", attributes = mapOf("rule" to rule))
            AppApm.recordEvent("app.sqlite.issue", mapOf("rule" to rule))
        }
    }

    fun queryExecutor() = callbackExecutor

    internal fun findIssues(sql: String, bindArgs: List<Any?> = emptyList()): Set<String> {
        val statement = stripSqlComments(sql).trim().trimEnd(';').trim()
        if (statement.isEmpty()) return emptySet()
        val issues = linkedSetOf<String>()
        if (selectStar.containsMatchIn(statement)) issues += "select_star"
        if (statement.startsWith("UPDATE", ignoreCase = true) &&
            updateWithoutWhere.matches(statement) && !Regex("\\bWHERE\\b", RegexOption.IGNORE_CASE).containsMatchIn(statement)
        ) issues += "update_without_where"
        if (statement.startsWith("DELETE", ignoreCase = true) &&
            deleteWithoutWhere.matches(statement) && !Regex("\\bWHERE\\b", RegexOption.IGNORE_CASE).containsMatchIn(statement)
        ) issues += "delete_without_where"
        if (Regex("\\bAUTOINCREMENT\\b", RegexOption.IGNORE_CASE).containsMatchIn(statement)) {
            issues += "autoincrement"
        }
        if (replaceConflict.containsMatchIn(statement)) {
            issues += "insert_or_replace"
        }
        if (literalPredicate.containsMatchIn(statement)) issues += "literal_predicate"
        if (leadingWildcard.containsMatchIn(statement) ||
            (likeParameter.containsMatchIn(statement) && bindArgs.any { it is String && it.startsWith("%") })
        ) issues += "leading_wildcard_like"
        return issues
    }

    private fun stripSqlComments(sql: String): String = sql
        .replace(Regex("--[^\\r\\n]*"), " ")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
}
