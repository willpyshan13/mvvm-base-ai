package com.will.mvvmbase.feature.inspection

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.will.mvvmbase.core.navigation.FeatureEntryBuilder
import com.will.mvvmbase.core.navigation.Navigator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class InspectionEntryBuilder @Inject constructor() : FeatureEntryBuilder {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: Navigator) {
        with(scope) {
            entry<InspectionsKey> {
                InspectionListRoute(onOpen = { navigator.navigate(InspectionEditorKey(it)) })
            }
            entry<InspectionEditorKey> { key ->
                InspectionEditorRoute(key.id, navigator::back)
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InspectionNavigationBindings {
    @Binds
    @IntoSet
    abstract fun bind(implementation: InspectionEntryBuilder): FeatureEntryBuilder
}
