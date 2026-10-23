package com.will.mvvmbase.feature.inspection

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object InspectionsKey : NavKey

@Serializable
data class InspectionEditorKey(val id: Long? = null) : NavKey
