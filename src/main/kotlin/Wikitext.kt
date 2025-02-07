object Wikitext  {

    /**
     * Find an {{Infobox <name> in the wikitext
     */

    fun findInfobox(wikitext: String, name: String): String? {
        val startInfobox = wikitext.indexOf("{{Infobox $name", 0, true)
        if (startInfobox >= 0) {
            val infobox = findClosingBraces(startInfobox, wikitext)
            if (infobox != null) {
                return infobox
            }
        }

        return null
    }


    /**
     * Find the closing braces of a template, ignoring nested templates
     */

    private fun findClosingBraces(start: Int, wikitext: String): String? {
        var depth = 0
        var end = start + 2
        while (end < wikitext.length) {
            val nextStart = wikitext.indexOf("{{", end)
            val nextEnd = wikitext.indexOf("}}", end)

            if (nextStart < 0 && nextEnd < 0) {
                println("No closing }} found")
                return null
            }

            if (nextEnd > 0 && (nextStart < 0 || nextEnd < nextStart)) {
                if (depth-- == 0) {
                    return wikitext.substring(start, nextEnd + 2)
                } else {
                    end = nextEnd + 2
                }
            } else {
                depth++
                end = nextStart + 2
            }
        }
        return null
    }

    fun findLinks(text: String): List<String> {
        val links = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = text.indexOf("[[", from)
            if (start < 0)
                break

            val end = text.indexOf("]]", start + 2)
            if (end < 0) {
                println("No end for link")
                break
            }

            val link = text.substring(start + 2, end)
            if (link.contains("|"))
                links.add(link.substringBefore("|"))
            else
                links.add(link)

            from = end + 2
        }
        return links
    }

    fun getKeyValuesFromInfobox(infobox: String): Map<String, String> {
        val keyValues = mutableMapOf<String, String>()
        var start = 0
        while (true) {
            val pipe = infobox.indexOf("|", start)
            if (pipe < 0)
                break

            val equals = infobox.indexOf("=", pipe)
            if (equals < 0)
                break

            val key = infobox.substring(pipe + 1, equals).trim()

            var end = equals + 1
            var templateDepth = 0
            var linkDepth = 0
            while (end < infobox.length) {

                val ch0 = infobox[end]
                if (ch0 == '|' && templateDepth == 0 && linkDepth == 0)
                    break

                if (end < infobox.length - 1 ) {
                    val ch1 = infobox[end + 1]

                    if (ch0 == '{' && ch1 == '{') {
                        templateDepth++
                        end++
                    }
                    else if (ch0 == '}' && ch1 == '}') {
                        templateDepth--
                        end++
                    }
                    else if (ch0 == '[' && ch1 == '[') {
                        linkDepth++
                        end++
                    }
                    else if (ch0 == ']' && ch1 == ']') {
                        linkDepth--
                        end++
                    }
                }

                end++
            }

            keyValues[key] = infobox.substring(equals + 1, end).trim()
            start = end
        }
        return keyValues
    }
}