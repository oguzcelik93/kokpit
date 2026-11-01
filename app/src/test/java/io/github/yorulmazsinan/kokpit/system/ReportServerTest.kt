package io.github.yorulmazsinan.kokpit.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportServerTest {
    @Test
    fun reportTextCannotBreakThePage() {
        assertEquals("&lt;/textarea&gt;&lt;script&gt;x&lt;/script&gt; &amp; &quot;", htmlEscape("</textarea><script>x</script> & \""))
    }

    @Test
    fun tokensDiffer() {
        assertTrue(ReportServer("a").token != ReportServer("a").token)
        assertEquals(32, ReportServer("a").token.length)
    }
}
