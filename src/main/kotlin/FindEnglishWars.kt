import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object FindEnglishWars {
    
    //  Run with --add-opens=java.base/java.net=ALL-UNNAMED

    var key: String = ""
    var path: String = ""

    val logging = false
    var tab = ""
    val tabSize = 2

    val wikitextDir = Storage.WikitextDirectory

    @JvmStatic
    fun main(args: Array<String>) {
        
        val interestingParticipants = setOf(
            "United Kingdom",
            "Kingdom of England",
            "Kingdom of Great Britain",
            "British Army",
            "English Army",
            "British Empire",
            "Royal Navy",
            "British America",
            "Middle Colonies",
            "Cavalier",
            "Roundhead",
            "Parliament of England",
            "Coalition forces of the Napoleonic Wars",
            "Allies of World War I",
            "Allies of World War II"
        )

        val debugging = "Q362"

        val articlesJson = File("$wikitextDir/wars.json").readText()
        val articles = objectReader.readValue(articlesJson, Map::class.java)

        println("Loaded ${articles.keys.size} articles")
        var found = 0
        val infoboxesPerPage = mutableMapOf<String, Map<String, String>>()
        val pageNamesToArticleIds = mutableMapOf<String, String>()

        for (article in articles) {

            if (article.key == debugging)
                println("Parsing ${article.key}")

            val articleId = article.key as String
            val articleName = article.value as String
            val wikitext = File("$wikitextDir/${articleId}.wiki").readText()
            val infobox = Wikitext.findInfobox(removeXmlComments(wikitext), "military conflict")
            if (infobox == null) {
                println("No infobox found in ${articleId}, ${articleName}")
                continue
            }

            var interesting = false
            val keyValues = Wikitext.getKeyValuesFromInfobox(infobox)

            val combatants = keyValues.filter { it.key.startsWith("combatant") && !it.key.contains("combatants_header") }.values
            for (combatant in combatants) {
                val name = combatant.substringAfter("=").trim()
                if (interestingParticipants.any(name::contains)) {
                    println("Found combatant in ${articleId}, ${articleName}")
                    interesting = true
                    break
                }
            }

            if (!interesting) {
                val place = keyValues["place"]
                if (place != null && place.contains("England")) {
                    println("Found England in ${articleId}, ${articleName}")
                    interesting = true
                }
            }

            if (interesting) {
                found++
                infoboxesPerPage[articleId] = keyValues
                pageNamesToArticleIds[articleName] = articleId
            }
        }

        println("Found $found articles")
        objectWriter.writeValue(File("$wikitextDir/interesting.json"), infoboxesPerPage)

/*
        for (articleId in infoboxesPerPage.keys) {
            val infobox = infoboxesPerPage[articleId]!!
            if (infobox.containsKey("partof")) {
                val partOf = infobox["partof"]!!
                val parents = Wikitext.findLinks(partOf)
                val parent = parents.firstOrNull { pageNamesToArticleIds.containsKey(it) }
                if (parent == null) {
                    //  We don't have a parent article.
                    if (missingPages.containsKey(partOf)) {
                        missingPages[partOf] = missingPages[partOf] + ", " + articles[articleId]
                    } else {
                        missingPages[partOf] = articles[articleId] as String
                    }
                }
                if (parent != null) {
                    hierarchy[articleId] = parent
                    println("${articles[articleId]} is part of $parent")
                } else if (parents.isNotEmpty()) {
                    println("${articles[articleId]} missing parent: ${parents.joinToString(", ")}")
                }
            }
        }
 */
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