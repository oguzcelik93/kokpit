package io.github.yorulmazsinan.kokpit.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class DialPadTest {
    @Test fun appendsDigits() = assertEquals("05324", dialAppend("0532", '4'))
    @Test fun backspaceRemovesLast() = assertEquals("0", dialBackspace("05"))
    @Test fun backspaceOnEmptyStaysEmpty() = assertEquals("", dialBackspace(""))
    @Test fun capsLength() = assertEquals(20, dialAppend("1".repeat(20), '2').length)
    @Test fun ignoresNonDialCharacters() = assertEquals("05", dialAppend("05", 'x'))
}
