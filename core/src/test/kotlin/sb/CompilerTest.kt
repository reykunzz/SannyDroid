package sb

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilerTest {
    private val assets = File("../app/src/main/assets")

    private fun db(): OpcodeDb {
        fun r(p: String) = File(assets, p).readText()
        return OpcodeDbLoader.load(
            listOf(r("sa/SASCM.INI"), r("sa_mobile/SASCM.Mobile.ini"), r("sa_mobile/SASCM.CLEO.ini"), r("sa_mobile/SASCM.ini")),
            r("sa_mobile/keywords.txt"),
            r("sa_mobile/constants.txt"),
            r("sa_mobile/sa_mobile.json"),
        )
    }

    private fun compileHex(src: String): String {
        val res = Compiler(db()).compile(src)
        assertTrue(res.errors.isEmpty(), res.errors.joinToString())
        val b = assertNotNull(res.bytes)
        return b.joinToString("") { "%02X".format(it) }
    }

    @Test
    fun databaseLoads() {
        val d = db()
        assertTrue(d.opcodes.size > 2000, "opcodes: ${d.opcodes.size}")
        assertTrue(d.keywords.containsKey("wait"))
    }

    @Test
    fun nopWaitTerminate() {
        assertEquals(
            "0000" + "0100" + "0400" + "4E00",
            compileHex("{\$CLEO .csa}\n0000:\nwait 0\nterminate_this_custom_script\n"),
        )
    }

    @Test
    fun assignments() {
        assertEquals("0400" + "023000" + "0405", compileHex("\$12 = 5"))
        assertEquals("0600" + "030000" + "0405", compileHex("0@ = 5"))
        assertEquals("0700" + "030300" + "06" + "0000C03F", compileHex("3@ = 1.5"))
    }

    @Test
    fun labelsAreRelativeNegative() {
        assertEquals("0000" + "0200" + "01FEFFFFFF", compileHex("0000:\n:L1\njump @L1\n"))
    }

    @Test
    fun undefinedLabelIsError() {
        val res = Compiler(db()).compile("0000:\njump @NOPE\n")
        assertTrue(res.errors.isNotEmpty())
    }
}
