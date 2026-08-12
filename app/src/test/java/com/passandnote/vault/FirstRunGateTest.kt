package com.passandnote.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunGateTest {
    @Test
    fun missingAcceptanceAlwaysShowsGate() {
        assertEquals(RootMode.BEFORE_CONTINUE, initialRootMode(false, false))
        assertEquals(RootMode.BEFORE_CONTINUE, initialRootMode(false, true))
    }

    @Test
    fun acceptedFreshInstallOpensOnboarding() {
        assertEquals(RootMode.ONBOARDING, initialRootMode(true, false))
    }

    @Test
    fun acceptedExistingVaultOpensUnlock() {
        assertEquals(RootMode.LOCKED, initialRootMode(true, true))
    }

    @Test
    fun acceptanceVersionMustBeCurrent() {
        assertFalse(hasAcceptedCurrentTerms(0))
        assertTrue(hasAcceptedCurrentTerms(CURRENT_TERMS_VERSION))
        assertTrue(hasAcceptedCurrentTerms(CURRENT_TERMS_VERSION + 1))
    }
}
