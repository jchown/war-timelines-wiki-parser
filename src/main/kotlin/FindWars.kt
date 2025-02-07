import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

object FindWars {
    
    //  Run with --add-opens=java.base/java.net=ALL-UNNAMED

    var key: String = ""
    var path: String = ""

    val logging = false
    var tab = ""
    val tabSize = 2

    val outputDir = "D:\\Work\\Data\\war-timelines-wikitext"
    val downloadedItems = mutableMapOf<String, String>()
    val failedItems = mutableListOf<ConflictPage>()

    class ConflictPage(val id: String, val name: String) {

        var retries = 0
        val maxRetries = 10

        private val filename = "$outputDir\\$id.wiki"

        fun download(): Boolean {
            val file = File(filename)
//            if (file.exists())
//                return true

            val cycleCheck = mutableSetOf<String>()

            var pageName = name
            while (true) {
                val text = loadWikitext(pageName)
                if (text.startsWith("<!DOCTYPE html>")) {
                    if (retries++ == 0) {
                        println("Failed to download $pageName ($id), will retry up to $maxRetries times")
                        return false
                    } else if (retries < maxRetries) {
                        return false
                    }
                    println("Failed to download $pageName ($id) after $maxRetries retries")
                    failedItems.add(this)
                    return true
                }

                // #REDIRECT [[Great Stand on the Ugra River]]
                if (text.startsWith("#REDIRECT [[")) {
                    val redirectedTo = text.substring(12, text.indexOf("]]"))
                    if (cycleCheck.contains(redirectedTo)) {
                        println("Redirect cycle detected")
                        return false
                    }

                    println("Redirecting from $pageName to $redirectedTo")
                    pageName = redirectedTo
                    cycleCheck.add(pageName)
                    continue
                }

                println("Downloaded $pageName")
                file.writeText(text)
                downloadedItems[id] = pageName
                return true
            }

        }
    }
    @JvmStatic
    fun main(args: Array<String>) {

        //  Filtered list from SPARQL query:
        //  SELECT ?item ?itemLabel WHERE {
        //    ?subclass wdt:P279* wd:Q71266556.
        //    SERVICE wikibase:label { bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en". }
        //  }
        //
        //  plus a couple that were missed

        val warTypes = mapOf(
            "Q12546" to	"Crusades",
            "Q2490042" to "Princely rebellion",
            "Q4348173" to "absolute war",
            "Q1897017" to "air battle",
            "Q350604" to "armed conflict",
            "Q178561" to "battle",
            "Q4927953" to "bloodless war",
            "Q107637520" to "border conflict",
            "Q5392931" to "cavalry raid",
            "Q8465" to "civil war",
            "Q180684" to "conflict",
            "Q2659056" to "colonial war",
            "Q429245" to "conventional warfare",
            "Q11552344" to "decisive warfare",
            "Q2518545" to "defensive war",
            "Q11682254" to "dirty war",
            "Q77597785" to "drug war",
            "Q766875" to "ethnic conflict",
            "Q48767773" to "engagement",
            "Q21978021" to "federal war",
            "Q9266805" to "general battle",
            "Q1190472" to "ground warfare",
            "Q13573188" to "holy war",
            "Q1323212" to "insurgency",
            "Q109311521" to "internal armed conflict",
            "Q2247326" to "limited war",
            "Q25512705" to "main battle",
            "Q15991159" to "medieval battle",
            "Q814290" to "meeting engagement",
            "Q831663" to "military campaign",
            "Q131782363" to "military conflict",
            "Q131656428" to "military convoy",
            "Q25416338" to "military convoy series",
            "Q1384277" to "military expedition",
            "Q7533291" to "military operation",
            "Q1348131" to "modern warfare",
            "Q1261499" to "naval battle",
            "Q17772965" to "naval capture",
            "Q743488" to "pitched battle",
            "Q9377327" to "preemptive war",
            "Q969619" to "preventive war",
            "Q864113" to "proxy war",
            "Q1827102" to "religious war",
            "Q111660983" to "resource war",
            "Q3119121" to "revolutionary war",
            "Q11396978" to "sanctions war",
            "Q3774758" to "sectarian violence",
            "Q104212151" to "series of wars",
            "Q4277662" to "short victorious war",
            "Q188055" to "siege",
            "Q12041935" to "siege in the Middle Ages",
            "Q997267" to "skirmish",
            "Q718893" to "theater of war",
            "Q154605" to "total war",
            "Q232777" to "two-front war",
            "Q1342628" to "unconventional warfare",
            "Q7883019" to "undeclared war",
            "Q198" to "war",
            "Q842332" to "war front",
            "Q752646" to "war of annihilation",
            "Q21994376" to "war of independence",
            "Q1006311" to "war of national liberation",
            "Q3119022" to "war of pacification",
            "Q1348385" to "war of succession",
            "Q105370834" to "war phase",
            "Q71266556" to "warfare and armed conflicts",
            "Q130525743" to "wars of individual sovereign",
            "Q103495" to "world war"
        )

        val interestingPages = warTypes.keys

        val wikidump = File("D:\\Work\\Data\\Wikidata\\wikidata-20241202-all.json.gz")

        val downloadQueue = Collections.synchronizedList(mutableListOf<ConflictPage>())
        val finished = AtomicBoolean(false)
        val numDownloaded = AtomicLong(0L)

        val downloader = Thread {
            while (!finished.get()) {

                Thread.sleep(1000)

                if (downloadQueue.size > 0) {
                    val page = downloadQueue.removeAt(0)
                    if (!page.download()) {
                        println("Failed to download ${page.name}")
                        downloadQueue.add(page)
                    } else {
                        numDownloaded.incrementAndGet()
                    }
                }
            }
            finished.set(true)
        }
        downloader.start()

        var numParsed = 1

        FileInputStream(wikidump).use { inStream ->

            GZIPInputStream(inStream).use { gzStream ->

                val jFactory = JsonFactory()
                val jParser: JsonParser = jFactory.createParser(gzStream)

                take(jParser, JsonToken.START_ARRAY)

                while (true) {

                    val item = readItem(jParser) ?: continue
                    if (item == noMoreItems)
                        break

                    if (++numParsed % 10000 == 0) {
                        println("Parsed $numParsed articles")
                    }
                    
                    if (!item.isInstanceOf(interestingPages)) {
                        continue
                    }

                    val name = item.englishName()
                    println("Found $name (${warTypes[item.instanceOf()]})")

                    val pageName = item.englishWikipediaPageName()
                    if (pageName == null) {
                        println("$name (${item.id}) has no English Wikipedia page")
                        continue
                    }

                    downloadQueue.add(ConflictPage(item.id, pageName))
                }
           }

            println("Parsed $numParsed articles")

            finished.set(true)
            downloader.join()

            println("Downloaded ${numDownloaded.get()} pages")
            println("Failed to download ${failedItems.size} pages:")

            failedItems.forEach {
                println("${it.name} (${it.id})")
            }

            save(downloadedItems)
        }
    }

    val objectMapper = ObjectMapper().writerWithDefaultPrettyPrinter()

    private fun save(items: Map<String, String>) {
        objectMapper.writeValue(File("$outputDir/wars.json"), items)
    }
    
    val noMoreItems = Item("", mapOf(), mapOf(), mapOf(), mapOf())

    private fun readItem(jParser: JsonParser): Item? {
        val token = nextToken(jParser)
        if (token == JsonToken.END_ARRAY)
            return noMoreItems
        
        if (token != JsonToken.START_OBJECT)
            throw Exception("Expected object but was $token")

        var id = "?"
        var name = mapOf<String, String>()
        var description = mapOf<String, String>()
        var claims = mapOf<String, String>()
        var sitelinks = mapOf<String, Map<String, String>>()

        do {
            when (val token = nextToken(jParser)) {
                JsonToken.END_OBJECT -> {
                    return Item(id, name, description, claims, sitelinks)
                }
                JsonToken.FIELD_NAME -> {
                    when (val key = jParser.valueAsString) {

                        "type" -> {
                            val typeValue = nextValueAsString(jParser)
                            if (typeValue != "item") {
                                
                                if (typeValue == "property") {      // Was a PXXX not a QXXX, e.g. a relationship type
                                    skipUntil(jParser, JsonToken.END_OBJECT)
                                    return null
                                }
                                
                                println("Expected 'item' but was '$typeValue'")
                                skipUntil(jParser, JsonToken.END_OBJECT)
                                return null
                            }
                        }

                        "id" -> {
                            id = nextValueAsString(jParser)
                        }

                        "labels" -> {
                            name = nextValueAsLanguageStrings(jParser)
                        }

                        "descriptions" -> {
                            description = nextValueAsLanguageStrings(jParser)
                        }

                        "claims" -> {
                            claims = nextValueAsClaims(jParser)
                        }

                        "sitelinks" -> {
                            sitelinks = nextValueAsSitelinks(jParser)
                        }

                        else -> {
                            logln("Skipping $key")

                            skipValue(jParser)
                        }
                    }
                }
                else -> {
                    throw Exception("Expected field or end of object, got $token")
                }
            }
        } while (true)
    }

    private fun nextFieldAsString(jParser: JsonParser, fieldName: String): String {

        while (true) {

            when (nextToken(jParser)) {
                JsonToken.START_OBJECT -> skipUntil(jParser, JsonToken.END_OBJECT)
                JsonToken.START_ARRAY -> skipUntil(jParser, JsonToken.END_ARRAY)
                JsonToken.FIELD_NAME -> {

                    val thisName = jParser.valueAsString
                    if (thisName == fieldName) {

                        return nextValueAsString(jParser)
                    }
                }
                else -> {}
            }
        }
    }

    private fun skipValue(jParser: JsonParser) {

        when (val nextToken = nextToken(jParser)) {
            JsonToken.START_OBJECT -> skipUntil(jParser, JsonToken.END_OBJECT)
            JsonToken.START_ARRAY -> skipUntil(jParser, JsonToken.END_ARRAY)
            else -> {
                if (!nextToken.isScalarValue)
                    throw Exception("Expected value, but was $nextToken")
            }
        }
    }

    private fun nextValueAsString(jParser: JsonParser): String {
        val value = nextToken(jParser)
        if (value != JsonToken.VALUE_STRING)
            throw Exception("Expected value to be string but was $value")

        return jParser.valueAsString
    }

    private fun nextValueAsLanguageStrings(jParser: JsonParser): Map<String, String> {
        val strings = mutableMapOf<String,String>()

        expectNextToken(jParser, JsonToken.START_OBJECT)

        while (true) {
            val next = nextToken(jParser)
            if (next == JsonToken.END_OBJECT)
                break

            if (next != JsonToken.FIELD_NAME)
                throw Exception("Expected field")

            val key = jParser.valueAsString
            expectNextToken(jParser, JsonToken.START_OBJECT)

            expectNextToken(jParser, JsonToken.FIELD_NAME)
            if (jParser.valueAsString != "language")
                throw Exception("Expected 'language' but was '${jParser.valueAsString}'")
            skipValue(jParser)

            expectNextToken(jParser, JsonToken.FIELD_NAME)
            if (jParser.valueAsString != "value")
                throw Exception("Expected 'value' but was '${jParser.valueAsString}'")

            strings[key] = nextValueAsString(jParser)

            expectNextToken(jParser, JsonToken.END_OBJECT)
        }

        return strings
    }

    private fun nextValueAsClaims(jParser: JsonParser): Map<String, String> {
        val claims = mutableMapOf<String,String>()

        expectNextToken(jParser, JsonToken.START_OBJECT)

        while (true) {
            val next = nextToken(jParser)
            if (next == JsonToken.END_OBJECT)
                break

            if (next != JsonToken.FIELD_NAME)
                throw Exception("Expected field")

            val key = jParser.valueAsString
            expectNextToken(jParser, JsonToken.START_ARRAY)
            expectNextToken(jParser, JsonToken.START_OBJECT)

            //println("claim: $key")
            //if (key == Item.INSTANCE_OF)
            //    println()

            expectNextToken(jParser, JsonToken.FIELD_NAME)
            if (jParser.valueAsString == "id") {
                skipValue(jParser)
                expectNextToken(jParser, JsonToken.FIELD_NAME)
            }

            if (jParser.valueAsString != "mainsnak")
                throw Exception("Expected 'mainsnak' but was '${jParser.valueAsString}'")

            val snak = nextValueAsSnak(jParser)
            if (snak != null)
                claims[key] = snak

            skipUntil(jParser, JsonToken.END_OBJECT)
            skipUntil(jParser, JsonToken.END_ARRAY)
        }

        return claims
    }

    private fun nextValueAsSitelinks(jParser: JsonParser): Map<String, Map<String, String>> {
        val sitelinks = mutableMapOf<String, Map<String, String>>()

        expectNextToken(jParser, JsonToken.START_OBJECT)

        while (true) {
            val next = nextToken(jParser)
            if (next == JsonToken.END_OBJECT)
                break

            if (next != JsonToken.FIELD_NAME)
                throw Exception("Expected field")

            val key = jParser.valueAsString
            sitelinks[key] = nextValueAsSitelink(jParser)
        }

        return sitelinks
    }

    private fun nextValueAsSitelink(jParser: JsonParser): Map<String, String> {
        val site = mutableMapOf<String,String>()

        expectNextToken(jParser, JsonToken.START_OBJECT)

        while (true) {
            val next = nextToken(jParser)
            if (next == JsonToken.END_OBJECT)
                break

            if (next != JsonToken.FIELD_NAME)
                throw Exception("Expected field")

            val key = jParser.valueAsString

            if (key == "site")
                skipValue(jParser)
            else if (key == "title")
                site[key] = nextValueAsString(jParser)
            else
                skipValue(jParser)
        }

        return site
    }


    private fun nextValueAsSnak(jParser: JsonParser): String? {

        expectNextToken(jParser, JsonToken.START_OBJECT)
        val obj = readObject(jParser)
        val snaktype = obj["snaktype"]
        when (snaktype) {
            "novalue" -> {
                return "(no value)"
            }
            "somevalue" -> {
                return Item.UNKNOWN
            }
            "value" -> {
                val datavalue = obj["datavalue"] as Map<String, Any>
                when (datavalue["type"]) {
                    "time" -> {
                        return asTime(obj)
                    }

                    "wikibase-entityid" -> {
                        return asId(obj)
                    }

                    else -> {
                        return "{}"
                    }
                }
            }
            else -> {
                throw Exception();
            }
        }
    }
    
    private fun loadWikitext(wikipedia: String): String {
        val url = "https://en.wikipedia.org/w/index.php?action=raw&title=$wikipedia"
        val response = khttp.get(url)
        return response.text
    }
    
    private fun findInfoxboxes(wiktext: String): List<String> {
        val infoboxes = mutableListOf<String>()

        var from = 0
        while (true) {
            val start = wiktext.indexOf("{{Infobox", from)
            if (start < 0)
                return infoboxes
    
            var depth = 0
            var end = start
            while (end < wiktext.length) {
                val nextEnd = wiktext.indexOf("}}", end)
                val nextStart = wiktext.indexOf("{{", end)
                if (nextStart == -1 || nextEnd < nextStart) {
                    if (depth == 0)
                        break
                    end = nextEnd + 2
                    depth--
                } else {
                    end = nextStart + 2
                    depth++
                }
            }
            infoboxes.add(wiktext.substring(start, end))
            from = end
        }
        
        return infoboxes
    }

    private fun getCombatantsFromInfobox(infobox: String): List<String> {
        val combatants = mutableListOf<String>()
        while (true) {
            val start = infobox.indexOf("| combatant" + (combatants.size + 1))
            if (start < 0)
                break
            var end = infobox.indexOf("\n|", start + 1)
            if (end < 0)
                end = infobox.length
            combatants.add(infobox.substring(start, end))
        }
        return combatants
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