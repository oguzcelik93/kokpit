package io.github.yorulmazsinan.kokpit.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneProjectionTest {
    @Test
    fun zlinkCountsKokpitDoesNot() {
        assertTrue(isProjectionApp("com.zjinnova.zlink"))
        assertFalse(isProjectionApp("io.github.oguzcelik93.kokpit93"))
        assertFalse(isProjectionApp(null))
    }
}
