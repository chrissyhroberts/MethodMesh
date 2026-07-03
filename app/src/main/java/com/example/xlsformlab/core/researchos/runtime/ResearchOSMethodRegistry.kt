package com.example.xlsformlab.core.researchos.runtime

import com.example.xlsformlab.core.Method
import com.example.xlsformlab.core.MethodCategory
import com.example.xlsformlab.core.MethodManifest
import com.example.xlsformlab.core.researchos.MethodContract
import com.example.xlsformlab.core.researchos.MethodDescriptor
import com.example.xlsformlab.modules.adminfingerprint.AdminFingerprintMethod
import com.example.xlsformlab.modules.adminfingerprint.As100VerifyFingerprintMethod
import com.example.xlsformlab.modules.calibratedscale.As100CalibratedScaleMethod
import com.example.xlsformlab.modules.calibratedscale.CalibratedScaleMethod
import com.example.xlsformlab.modules.gpstargetnavigator.As100LocateTargetMethod
import com.example.xlsformlab.modules.gpstargetnavigator.GpsTargetNavigatorMethod
import com.example.xlsformlab.modules.nfc.As100NfcReadMethod
import com.example.xlsformlab.modules.nfc.As100NfcWriteMethod
import com.example.xlsformlab.modules.nfc.NfcReadMethod
import com.example.xlsformlab.modules.nfc.NfcWriteMethod

/**
 * Canonical AS1.00-facing registry of executable methods.
 *
 * During migration, existing Method implementations are owned here and
 * exposed as As100Method instances via a legacy adapter. The old
 * MethodRegistry now delegates to this registry so the conceptual centre of
 * the app is Method, not legacy methods.
 */
object As100MethodRegistry {

    private val legacyMethods: List<Method> by lazy {
        listOf(
            CalibratedScaleMethod(),
            AdminFingerprintMethod(),
            GpsTargetNavigatorMethod(),
            NfcReadMethod(),
            NfcWriteMethod()
        )
    }

    private val methods: List<As100Method> by lazy {
        listOf(
            As100NfcReadMethod,
            As100NfcWriteMethod,
            As100CalibratedScaleMethod,
            As100LocateTargetMethod,
            As100VerifyFingerprintMethod
        ) + legacyMethods
            .filterNot { method -> method.manifest.id in setOf(
                As100NfcReadMethod.ID,
                As100NfcWriteMethod.ID,
                As100CalibratedScaleMethod.ID,
                As100LocateTargetMethod.ID,
                As100VerifyFingerprintMethod.ID
            ) }
            .map { method -> As100LegacyMethodAdapter(method) }
    }

    fun all(): List<As100Method> = methods

    fun find(id: String): As100Method? =
        methods.firstOrNull { it.id == id }

    fun require(id: String): As100Method =
        find(id) ?: error("No AS1.00 method registered with id: $id")

    fun descriptors(): List<MethodDescriptor> =
        methods.map { it.descriptor }

    fun contracts(): List<MethodContract> =
        methods.map { it.contract }

    /** Legacy compatibility surface for UI/transport still being migrated. */
    fun legacyMethods(): List<Method> = legacyMethods

    fun legacyFind(id: String): Method? =
        legacyMethods.firstOrNull { it.manifest.id == id }

    fun legacyRequire(id: String): Method =
        legacyFind(id) ?: error("No legacy method registered with id: $id")

    fun legacyByCategory(category: MethodCategory): List<Method> =
        legacyMethods.filter { it.manifest.category == category }

    fun legacyCategoriesInUse(): List<MethodCategory> =
        legacyMethods.map { it.manifest.category }.distinct()

    fun legacyManifests(): List<MethodManifest> =
        legacyMethods.map { it.manifest }
}
