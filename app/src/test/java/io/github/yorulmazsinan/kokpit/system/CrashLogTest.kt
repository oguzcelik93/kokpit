package io.github.yorulmazsinan.kokpit.system

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun reportContainsVersionThreadAndStackTrace() {
        val report = crashReport(IllegalStateException("kaboom"), threadName = "main", version = "0.2.1", timeMs = 0L)
        assertTrue("version" , "0.2.1" in report)
        assertTrue("thread", "main" in report)
        assertTrue("message", "IllegalStateException: kaboom" in report)
        assertTrue("stack", "CrashLogTest" in report)
    }

    @Test
    fun appendKeepsEarlierCrashes() {
        val file = File(tmp.root, "kokpit-crash.txt")
        appendCrash(file, "first")
        appendCrash(file, "second")
        val text = file.readText()
        assertTrue("first" in text && "second" in text)
    }
}
