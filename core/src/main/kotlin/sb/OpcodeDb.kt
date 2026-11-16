package sb

import kotlinx.serialization.json.*

enum class Src { ANY, GLOBAL, LOCAL, LITERAL }

data class ParamSpec(val type: String, val source: Src)

data class Suggestion(val word: String, val usage: String)

private val PLACEHOLDER = Regex("%(\\d+)([a-zA-Z])([^%]*)%")

private fun editDistance(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                v = minOf(v, d[i - 2][j - 2] + 1)
            }
            d[i][j] = v
        }
    }
    return d[a.length][b.length]
}

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
    var name: String = "",
    var description: String = "",
) {
    var specs: List<ParamSpec> = emptyList()
    val variadic: Boolean get() = argc < 0

    /** Bentuk pola yang mudah dibaca, mis. "wait <nilai> ms". */
    fun friendly(): String = pattern.replace(PLACEHOLDER) { m ->
        when (m.groupValues[2][0]) {
            'p' -> "@label"
            'g' -> "'KUNCI'"
            's', 'k' -> "'teks'"
            'o', 'm' -> "model"
            else -> "nilai"
        }
    }
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

    /** Perintah yang ejaannya paling mirip dengan [word] (untuk pesan salah ketik). */
    fun suggest(word: String, max: Int = 3): List<Suggestion> {
        val w = word.lowercase()
        if (w.isEmpty()) return emptyList()
        val limit = maxOf(1, minOf(3, w.length / 3))
        val scored = ArrayList<Pair<Int, String>>()
        val seen = HashSet<String>()
        for (k in keywords.keys) {
            if (seen.add(k)) {
                val d = editDistance(w, k)
                if (d <= limit) scored.add(d to k)
            }
        }
        for (k in byWord.keys) {
            if (seen.add(k)) {
                val d = editDistance(w, k)
                if (d <= limit) scored.add(d to k)
            }
        }
        scored.sortWith(compareBy({ it.first }, { it.second }))
        val out = ArrayList<Suggestion>()
        val usages = HashSet<String>()
        for ((_, cand) in scored) {
            val id = keywords[cand]
            val op = (if (id != null) opcodes[id] else null) ?: byWord[cand]?.firstOrNull() ?: continue
            val usage = op.friendly()
            if (usages.add(usage)) out.add(Suggestion(cand, usage))
            if (out.size >= max) break
        }
        return out
    }
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
                op.name = o["name"]?.jsonPrimitive?.content ?: ""
                op.description = o["short_desc"]?.jsonPrimitive?.content ?: ""
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
