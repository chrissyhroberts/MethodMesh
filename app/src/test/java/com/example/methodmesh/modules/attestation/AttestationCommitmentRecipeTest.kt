package com.example.methodmesh.modules.attestation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationCommitmentRecipeTest {
    @Test
    fun `valid v1 recipe is accepted and hashed exactly`() {
        val recipe = """{"schema":"methodmesh.commitment_recipe.v1","canonicalization":"ordered-kv-v1","hash_algorithm":"SHA-256","members":[{"path":"photo_redacted_image_sha256","type":"sha256","commitment":"artifact-bytes-sha256","artifact_field":"photo_redacted_image_uri"}]}"""

        assertEquals(recipe, AttestationCommitmentRecipe.validate(recipe))
        assertEquals(AttestationCrypto.sha256Hex(recipe), AttestationCommitmentRecipe.sha256(recipe))
    }

    @Test
    fun `commitment recipe is mandatory`() {
        val failed = runCatching { AttestationCommitmentRecipe.validate("") }.isFailure

        assertTrue(failed)
    }

    @Test
    fun `malformed recipe json is rejected`() {
        val failed = runCatching { AttestationCommitmentRecipe.validate("{nope") }.isFailure

        assertTrue(failed)
    }

    @Test
    fun `unsupported recipe schema is rejected`() {
        val failed = runCatching {
            AttestationCommitmentRecipe.validate(
                """{"schema":"other","canonicalization":"ordered-kv-v1","hash_algorithm":"SHA-256","members":[]}"""
            )
        }.isFailure

        assertTrue(failed)
    }

    @Test
    fun `unsupported commitment type is rejected`() {
        val failed = runCatching {
            AttestationCommitmentRecipe.validate(
                """{"schema":"methodmesh.commitment_recipe.v1","canonicalization":"ordered-kv-v1","hash_algorithm":"SHA-256","members":[{"path":"x","type":"string","commitment":"execute-javascript"}]}"""
            )
        }.isFailure

        assertTrue(failed)
    }
}
