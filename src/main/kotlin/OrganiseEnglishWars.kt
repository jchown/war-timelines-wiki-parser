import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

object OrganiseEnglishWars {
    
    //  Run with --add-opens=java.base/java.net=ALL-UNNAMED

    var key: String = ""
    var path: String = ""

    val logging = false
    var tab = ""
    val tabSize = 2

    val wikitextDir = "D:\\Work\\Data\\war-timelines-wikitext"
    val root = "<root>"

    @JvmStatic
    fun main(args: Array<String>) {

        val articlesJson = File("$wikitextDir/wars.json").readText()
        val articles = objectReader.readValue(articlesJson, Map::class.java) as Map<String, String>
        val infoboxes = objectReader.readValue(File("$wikitextDir/interesting.json").readText(), Map::class.java) as Map<String, Map<String, String>>
        val pageNamesToArticleIds = articles.map { (it.value as String).lowercase() to (it.key as String) }.toMap()
        //  Make case insensitive

        /*  Look for duplicates
        for (pageMapping in pageNamesToArticleIds) {
            val articleId = pageMapping.value
            if (articles[articleId].key == debugging) {
                println("Found $debugging")
            }
        }
        */

        println("Loaded ${infoboxes.keys.size} infoboxes")

        val hierarchy = mutableMapOf<String, String>()
        val toFindParent = infoboxes.keys.toMutableList()

        while (!toFindParent.isEmpty()) {

            val articleId = toFindParent.removeAt(0)
            val infobox = infoboxes[articleId]!!

            if (infobox.containsKey("partof")) {
                val partOf = infobox["partof"]!!
                val partOfLinks = Wikitext.findLinks(partOf)
                val parents = partOfLinks.filter { pageNamesToArticleIds.containsKey(it.lowercase()) }

                //  Only choose a parent if we already have it in the hierarchy

                if (parents.size == 0) {

                    hierarchy[articleId] = root
                    println("${articles[articleId]} is a root article")

                } else if (parents.size == 1) {

                    val parent = parents.first()
                    val parentId = pageNamesToArticleIds[parent.lowercase()]!!
                    hierarchy[articleId] = parentId
                    println("${articles[articleId]} is part of $parent")

                } else {

                    //  Multiple candidates - we will choose the deepest (most specific) one,
                    //  if they've all been added to the hierarchy already.

                    if (parents.all { hierarchy.containsKey(pageNamesToArticleIds[it.lowercase()]!!) }) {

                        var deepestDepth = -1
                        var deepestName = ""

                        for (i in 0 .. parents.size) {
                            val depth = getDepth(pageNamesToArticleIds[parents[i]]!!, hierarchy)
                            if (depth > deepestDepth) {
                                deepestDepth = depth
                                deepestName = parents[i]
                            }
                        }

                        hierarchy[articleId] = pageNamesToArticleIds[deepestName.lowercase()]!!
                        println("${articles[articleId]} is part of $deepestName")

                    } else {

                        toFindParent.add(articleId)
                        println("Not ready to add ${articles[articleId]} tp the hierarchy")

                    }
                }
            } else {

                hierarchy[articleId] = root
                println("${articles[articleId]} is a root article")

            }
        }
    }

    private fun getDepth(articleId: String, hierarchy: MutableMap<String, String>): Int {
        if (articleId == root)
            return 0
        if (!hierarchy.containsKey(articleId))
            throw Exception("No hierarchy for $articleId")
        return 1 + getDepth(hierarchy[articleId]!!, hierarchy)
    }

    private fun removeXmlComments(wikitext: String): String {
        return wikitext.replace(Regex("<!--.*?-->"), "")
    }

    val objectReader = ObjectMapper().reader()
    val objectWriter = ObjectMapper().writerWithDefaultPrettyPrinter()

    private fun findLineStarting(lines: List<String>, start: String, from: Int): Int {
        for (i in from until lines.size) {
            if (lines[i].startsWith(start))
                return i
        }
        return -1
    }

    private fun findLinesStartingWith(lines: List<String>, start1: String, start2: String, from: Int): Int {
        for (i in from until lines.size - 1) {
            if (lines[i].startsWith(start1) && lines[i+1].startsWith(start2)) {
                return i
            }
        }
        return -1
    }

    private fun findLineWithEmptyLine(lines: List<String>, line: String, from: Int): Int {
        for (i in from until lines.size) {
            if (i > 0 && lines[i].isEmpty() && lines[i - 1] == line) {
                return i
            }
        }
        return -1
    }

    private fun findLineStartingWithEmptyLine(lines: List<String>, start: String, from: Int): Int {
        for (i in from until lines.size) {
            if (i > 0 && lines[i].isEmpty() && lines[i - 1].startsWith(start)) {
                return i
            }
        }
        return -1
    }

    private fun findEmptyLine(lines: List<String>, from: Int): Int {
        for (i in from until lines.size) {
            if (lines[i].isEmpty())
                return i
        }
        return -1
    }

    val PROLEPTIC_GREGORIAN = "http://www.wikidata.org/entity/Q1985727"
    val PROLEPTIC_JULIAN = "http://www.wikidata.org/entity/Q1985786"

    val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd G")

    private fun asTime(obj: Map<String, Any>): String? {
        val datavalue: Map<String, Any> = obj["datavalue"] as Map<String, Any>
        val value: Map<String, Any> = datavalue["value"] as Map<String, Any>

        val precision = value["precision"] as String
        if (precision != "11")
            return null


        //val timezone = value["timezone"] as String
        //if (timezone != "0")
        //    throw Exception("timezone not 0")

        val calendar = value["calendarmodel"] as String
        val daysOffset = when (calendar) {
            PROLEPTIC_GREGORIAN -> 0
            PROLEPTIC_JULIAN -> 13
            else -> throw Exception("Calendar is $calendar not Gregorian ($PROLEPTIC_GREGORIAN)")
        }

        val time = value["time"] as String
        val eraAD = (time[0] == '+')
        val ymd = time.substring(1, 11)
        val suffix = if (eraAD) "AD" else "BC"

        return try {
            val datetime = LocalDate.parse("$ymd $suffix", dateFormat).plusDays(daysOffset.toLong())
            datetime.toEpochDay().toString()
        } catch (e: Exception) {
            println("Bad date: ${e.message}")
            null
        }
    }

    private fun asId(obj: Map<String, Any>): String {
        val datavalue: Map<String, Any> = obj["datavalue"] as Map<String, Any>
        val value: Map<String, Any> = datavalue["value"] as Map<String, Any>

        return value["id"] as String
    }

    private fun readObject(jParser: JsonParser): Map<String, Any> {
        val obj = mutableMapOf<String,Any>()

        while (true) {
            val next = nextToken(jParser)
            if (next == JsonToken.END_OBJECT)
                break

            if (next != JsonToken.FIELD_NAME)
                throw Exception("Expected field")

            val key = jParser.valueAsString
            val value: Any = when (nextToken(jParser)) {
                JsonToken.START_OBJECT -> {
                    readObject(jParser)
                }
                JsonToken.START_ARRAY -> {
                    readArray(jParser)
                }
                else -> {
                    jParser.valueAsString ?: "null"
                }
            }

            obj[key] = value
        }

        return obj
    }

    private fun readArray(jParser: JsonParser): Array<Any> {
        val arr = mutableListOf<Any>()

        while (true) {
            val next = nextToken(jParser)
            if (next == JsonToken.END_ARRAY)
                break

            arr.add(when (nextToken(jParser)) {
                JsonToken.START_OBJECT -> {
                    readObject(jParser)
                }
                JsonToken.START_ARRAY -> {
                    readArray(jParser)
                }
                else -> {
                    jParser.valueAsString
                }
            })
        }

        return arr.toTypedArray()
    }

    private fun expectNextToken(jParser: JsonParser, expectedToken: JsonToken) {

        val nextToken = nextToken(jParser)
        if (nextToken != expectedToken)
            throw Exception("Expected value to be $expectedToken but was $nextToken")
    }

    private fun take(jParser: JsonParser, expectedToken: JsonToken) {
        val token = nextToken(jParser)
        if (token != expectedToken)
            throw Exception("Expected token type of $expectedToken but was $token")
    }

    private fun skipUntil(jParser: JsonParser, token: JsonToken) {
        while (true) {
            val next = nextToken(jParser)
            if (next == token)
                return

            when (next) {
                JsonToken.START_OBJECT -> skipUntil(jParser, JsonToken.END_OBJECT)
                JsonToken.START_ARRAY -> skipUntil(jParser, JsonToken.END_ARRAY)
                else -> {}
            }
        }
    }

    private fun nextToken(jParser: JsonParser): JsonToken {
        val token = jParser.nextToken()!!
        when (token) {
            JsonToken.NOT_AVAILABLE -> logln("n/a")
            JsonToken.START_OBJECT -> { logln("\n$tab{"); tabIn() }
            JsonToken.END_OBJECT -> { tabOut(); logln("$tab}") }
            JsonToken.START_ARRAY -> { logln("\n$tab["); tabIn() }
            JsonToken.END_ARRAY -> { tabOut(); logln("$tab]") }
            JsonToken.FIELD_NAME -> { log("$tab\"${jParser.valueAsString}\":") }
            JsonToken.VALUE_EMBEDDED_OBJECT -> log("\$object")
            JsonToken.VALUE_STRING -> logln("\"${jParser.valueAsString}\"")
            JsonToken.VALUE_NUMBER_INT -> logln("${jParser.valueAsLong}")
            JsonToken.VALUE_NUMBER_FLOAT -> logln("${jParser.valueAsDouble}")
            JsonToken.VALUE_TRUE -> logln("true")
            JsonToken.VALUE_FALSE -> logln("false")
            JsonToken.VALUE_NULL -> logln("null")
        }

        key = if (token == JsonToken.FIELD_NAME)
            jParser.valueAsString;
        else
            ""

        return token
    }

    private fun logln(s: String) {
        if (logging)
            println(s)
    }

    private fun log(s: String) {
        if (logging)
            print(s)
    }

    private fun tabIn() {
        for (i in 1..tabSize) {
            tab += " "
        }

        path += ":$key"

        if (path == ":::claims")
            path += ""
    }

    private fun tabOut() {
        tab = tab.substring(0, tab.length - tabSize)
        path = path.substring(0, path.lastIndexOf(':'))
    }
}