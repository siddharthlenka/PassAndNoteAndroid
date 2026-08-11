package com.passandnote.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleOperationGateTest {
    @Test
    fun secondOperationIsRejectedUntilOwnerFinishes() {
        val gate = SingleOperationGate()
        val first = checkNotNull(gate.tryBegin())

        assertNull(gate.tryBegin())
        assertTrue(gate.owns(first))
        assertTrue(gate.finish(first))

        val second = checkNotNull(gate.tryBegin())
        assertNotSame(first, second)
        assertTrue(gate.owns(second))
    }

    @Test
    fun staleCallbackCannotFinishNewerOperation() {
        val gate = SingleOperationGate()
        val first = checkNotNull(gate.tryBegin())
        assertTrue(gate.finish(first))
        val second = checkNotNull(gate.tryBegin())

        assertFalse(gate.finish(first))
        assertFalse(gate.owns(first))
        assertTrue(gate.owns(second))
        assertTrue(gate.finish(second))
        assertFalse(gate.finish(second))
    }
}
