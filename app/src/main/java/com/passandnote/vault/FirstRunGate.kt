package com.passandnote.vault

internal const val CURRENT_TERMS_VERSION = 1

internal fun hasAcceptedCurrentTerms(storedVersion: Int): Boolean =
    storedVersion >= CURRENT_TERMS_VERSION

internal fun initialRootMode(hasAcceptedTerms: Boolean, hasCreatedVault: Boolean): RootMode = when {
    !hasAcceptedTerms -> RootMode.BEFORE_CONTINUE
    hasCreatedVault -> RootMode.LOCKED
    else -> RootMode.ONBOARDING
}
