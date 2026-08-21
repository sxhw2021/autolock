package com.demo.autolock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeoutValidatorTest {

    @Test fun `valid lower bound`() {
        assertEquals(1, TimeoutValidator.parse("1"))
    }

    @Test fun `valid upper bound`() {
        assertEquals(720, TimeoutValidator.parse("720"))
    }

    @Test fun `valid middle value trims whitespace`() {
        assertEquals(15, TimeoutValidator.parse(" 15 "))
    }

    @Test fun `zero rejected`() {
        assertNull(TimeoutValidator.parse("0"))
    }

    @Test fun `above max rejected`() {
        assertNull(TimeoutValidator.parse("721"))
    }

    @Test fun `negative rejected`() {
        assertNull(TimeoutValidator.parse("-5"))
    }

    @Test fun `non numeric rejected`() {
        assertNull(TimeoutValidator.parse("abc"))
    }

    @Test fun `empty rejected`() {
        assertNull(TimeoutValidator.parse(""))
    }
}
