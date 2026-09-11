package com.example.methodmesh.modules

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Optional in-app control contributed by module metadata. The shell owns
 * placement and ordering; the module owns visibility, state and permissions.
 * Content must wrap its bounds and must not block the rest of the app.
 */
interface ModuleOverlaySpec {
    val id: String
    val order: Int get() = 0
    @Composable fun Render(modifier: Modifier)
}
