package com.kmpfeaturekit.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.NavigationType

@State(name = "KmpFeatureKitSettings", storages = [Storage("kmpFeatureKit.xml")])
@Service(Service.Level.PROJECT)
class KmpFeatureKitSettings : PersistentStateComponent<KmpFeatureKitSettings.State> {
    data class State(
        var defaultArchitecture: String = ArchitectureType.MVVM.name,
        var defaultNavigation: String = NavigationType.NONE.name,
        var defaultDi: String = DependencyInjectionType.KOIN.name,
        var defaultPackagePattern: String = "features.{featureName}",
        var routeStyle: String = "typed-object",
        var generatePreviews: Boolean = true,
        var inspectionSeverity: String = "WARNING"
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
