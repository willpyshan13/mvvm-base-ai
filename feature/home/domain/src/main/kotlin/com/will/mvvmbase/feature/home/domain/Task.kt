package com.will.mvvmbase.feature.home.domain

data class Task(
    val id: Long,
    val title: String,
    val isCompleted: Boolean = false,
)

enum class TaskFilter(val databaseValue: Int) {
    ALL(-1),
    ACTIVE(0),
    COMPLETED(1),
}

