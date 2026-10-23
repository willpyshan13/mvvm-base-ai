package com.will.mvvmbase.feature.home

import androidx.navigation3.runtime.NavKey
import com.will.mvvmbase.core.navigation.AuthRequiredNavKey
import kotlinx.serialization.Serializable

@Serializable
data object TasksKey : NavKey

@Serializable
data object SummaryKey : AuthRequiredNavKey
