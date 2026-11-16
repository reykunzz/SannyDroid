package sb

import kotlinx.serialization.json.*

enum class Src { ANY, GLOBAL, LOCAL, LITERAL }

data class ParamSpec(val type: String, val source: Src)

class Opcode(
    val id: Int,
    val argc: Int,
    val pattern: String,
    val regex: Regex,
    /** group i (0-based) -> parameter number (1-based) */
    val groupToParam: List<Int>,
    /** parameter number (1-based) -> placeholder letter (d, g, p, s, ...) */
    val letters: Map<Int, Char>,
    val firstWord: String?,
) {
    var specs: List<ParamSpec> = emptyList()
    val variadic: Boolean get() = argc < 0
}

class OpcodeDb(
    val opcodes: Map<Int, Opcode>,
    /** keyword (lowercase) -> opcode id */
    val keywords: Map<String, Int>,
    /** CONSTANT (uppercase) -> replacement text */
    val constants: Map<String, String>,
) {
    private val byWord: Map<String, List<Opcode>>
    private val generic: List<Opcode>

    init {
        val w = HashMap<String, MutableList<Opcode>>()
        val g = ArrayList<Opcode>()
        for (op in opcodes.values) {
            val fw = op.firstWord
            if (fw == null) g.add(op) else w.getOrPut(fw) { ArrayList() }.add(op)
        }
        byWord = w
        generic = g
    }

    fun candidates(word: String): List<Opcode> = (byWord[word] ?: emptyList()) + generic
}

object OpcodeDbLoader {
    private val iniLine = Regex("^([0-9A-Fa-f]{4})=(-?\\d+),(.*)$")
    private val ph = Regex("%(\\d+)([a-zA-Z])([^%]*)%")
    private const val CAPTURE = "(\"[^\"]*\"|'[^']*'|\\S+?)"

    /**
     * @param iniTexts opcode ini files in load order (later files override earlier ones)
     */
    fun load(iniTexts: List<String>, keywordsTxt: String, constantsTxt: String, libraryJson: String?): OpcodeDb {
        val ops = LinkedHashMap<Int, Opcode>()
        for (text in iniTexts) {
            for (raw in text.lines()) {
                val m = iniLine.matchEntire(raw.trim()) ?: continue
                val id = m.groupValues[1].toInt(16)
                val argc = m.groupValues[2].toInt()
                val pattern = m.groupValues[3].substringBefore(';').trim()
                ops[id] = buildOpcode(id, argc, pattern)
            }
        }
        if (libraryJson != null) applyLibrary(ops, libraryJson)

        val kw = HashMap<String, Int>()
        for (raw in keywordsTxt.lines()) {
            val l = raw.trim()
            if (l.startsWith(";")) continue
            val m = Regex("^([0-9A-Fa-f]{4})=(\\S+)").find(l) ?: continue
            kw[m.groupValues[2].lowercase()] = m.groupValues[1].toInt(16)
        }

        val consts = HashMap<String, String>()
        var inConst = false
        for (raw in constantsTxt.lines()) {
            val l = raw.trim()
            when {
                l.equals("const", true) -> inConst = true
                l.equals("end", true) || l.equals("var", true) -> inConst = false
                inConst && l.contains('=') && !l.startsWith(";") ->
                    consts[l.substringBefore('=').trim().uppercase()] = l.substringAfter('=').trim()
            }
        }
        return OpcodeDb(ops, kw, consts)
    }

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun buildOpcode(id: Int, argc: Int, pattern: String): Opcode {
        val src = pattern.trim()
        val sb = StringBuilder("^\\s*")
        val order = ArrayList<Int>()
        val letters = HashMap<Int, Char>()
        var i = 0
        var last: Char? = null // last consumed pattern char; placeholders count as word-like 'x'
        while (i < src.length) {
            val c = src[i]
            if (c == '%') {
                val m = ph.matchAt(src, i)
                if (m != null) {
                    val n = m.groupValues[1].toInt()
                    sb.append(CAPTURE)
                    order.add(n)
                    letters[n] = m.groupValues[2][0]
                    i += m.value.length
                    last = 'x'
                    continue
                }
            }
            if (c.isWhitespace()) {
                var j = i
                while (j < src.length && src[j].isWhitespace()) j++
                val nextWord = j < src.length && ((src[j] == '%' && ph.matchAt(src, j) != null) || isWord(src[j]))
                val prevWord = last != null && isWord(last)
                sb.append(if (prevWord && nextWord) "\\s+" else "\\s*")
                i = j
                last = ' '
                continue
            }
            sb.append(Regex.escape(c.toString()))
            last = c
            i++
        }
        if (argc < 0) sb.append("(?:\\s+(.+?))?")
        sb.append("\\s*$")
        val fw = src.takeWhile { isWord(it) }.lowercase().ifEmpty { null }
        return Opcode(id, argc, src, Regex(sb.toString(), RegexOption.IGNORE_CASE), order, letters, fw)
    }

    private fun applyLibrary(ops: Map<Int, Opcode>, json: String) {
        val root = Json.parseToJsonElement(json).jsonObject
        val exts = root["extensions"] as? JsonArray ?: return
        for (ext in exts) {
            val cmds = ext.jsonObject["commands"] as? JsonArray ?: continue
            for (cmd in cmds) {
                val o = cmd.jsonObject
                val id = o["id"]?.jsonPrimitive?.content?.toIntOrNull(16) ?: continue
                val op = ops[id] ?: continue
                val specs = ArrayList<ParamSpec>()
                for (key in listOf("input", "output")) {
                    val arr = o[key] as? JsonArray ?: continue
                    for (p in arr) {
                        val po = p.jsonObject
                        val type = po["type"]?.jsonPrimitive?.content ?: ""
                        val src = when (po["source"]?.jsonPrimitive?.content) {
                            "var_global" -> Src.GLOBAL
                            "var_local" -> Src.LOCAL
                            "literal" -> Src.LITERAL
                            else -> Src.ANY
                        }
                        specs.add(ParamSpec(type, src))
                    }
                }
                op.specs = specs
            }
        }
    }
}
