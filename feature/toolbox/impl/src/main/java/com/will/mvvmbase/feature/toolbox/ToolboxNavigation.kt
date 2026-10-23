package com.will.mvvmbase.feature.toolbox

import androidx.navigation3.runtime.*
import androidx.compose.foundation.layout.padding
import com.will.mvvmbase.feature.toolbox.R
import com.will.mvvmbase.core.navigation.*
import com.will.mvvmbase.feature.media.VideoPlayerKey
import com.will.mvvmbase.feature.status.StatusKey
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class ToolboxEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            entry<ToolboxKey> { ToolboxScreen(navigator) }
            entry<ResultPickerKey> {
                val resources = androidx.compose.ui.platform.LocalContext.current.resources
                androidx.compose.material3.Scaffold(topBar = {
                    com.will.mvvmbase.core.designsystem.components.AppTopBar(resources.getString(R.string.picker_title), navigator::back)
                }) { padding ->
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(padding)) {
                        listOf("A" to R.string.option_a, "B" to R.string.option_b).forEach { (value, label) ->
                            androidx.compose.material3.TextButton(onClick = { navigator.returnResult(ToolboxResult, value) }) {
                                androidx.compose.material3.Text(resources.getString(label))
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = navigator::back) {
                            androidx.compose.material3.Text(resources.getString(R.string.picker_cancel))
                        }
                    }
                }
            }
        }
    }
}
@Module @InstallIn(SingletonComponent::class)
abstract class ToolboxBindings {
    @Binds @IntoSet abstract fun entry(implementation: ToolboxEntryBuilder): FeatureEntryBuilder
}
