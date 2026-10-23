package com.will.mvvmbase.feature.toolbox

import com.will.mvvmbase.core.navigation.NavigationResultKey
import kotlinx.serialization.builtins.serializer

val ToolboxResult = NavigationResultKey("toolbox.selection", String.serializer())
