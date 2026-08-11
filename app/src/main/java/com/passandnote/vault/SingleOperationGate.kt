package com.passandnote.vault

/**
 * Main-thread-confined ownership gate for an asynchronous operation.
 * A stale callback can never finish or mutate a newer operation.
 */
internal class SingleOperationGate {
    internal class Token internal constructor()

    private var activeToken: Token? = null

    fun tryBegin(): Token? {
        if (activeToken != null) return null
        return Token().also { activeToken = it }
    }

    fun owns(token: Token): Boolean = activeToken === token

    fun finish(token: Token): Boolean {
        if (!owns(token)) return false
        activeToken = null
        return true
    }
}
