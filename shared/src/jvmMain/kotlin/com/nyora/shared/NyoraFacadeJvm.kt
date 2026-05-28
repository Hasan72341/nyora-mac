package com.nyora.shared

import com.nyora.shared.extension.JvmExtensionRuntime
import com.nyora.shared.repository.JsonLibraryRepository

object NyoraFacadeFactory {
    fun create(): NyoraFacade = NyoraFacade(
        repository = JsonLibraryRepository(),
        runtime = JvmExtensionRuntime(),
    )
}
