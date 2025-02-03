class Item(
    val id: String,
    val name: Map<String, String>,
    val description: Map<String, String>,
    val claims: Map<String, String>,
    val sitelinks: Map<String, Map<String, String>>
) {

    companion object {
        val HUMAN = "Q5"    
        val FICTIONAL_HUMAN = "Q15632617"
        val ARMED_CONFLICT = "Q350604"
        val MILITARY_CONFLICT = "Q831663"
        val BATTLE = "Q178561"
        val WAR = "Q198"

        val INSTANCE_OF = "P31"
        val DATE_OF_BIRTH = "P569"
        val DATE_OF_DEATH = "P570"

        val UNKNOWN = "???"
        
        val keyNames = mapOf(
            "Q5" to "human",
            WAR to "war",
            BATTLE to "battle",
            MILITARY_CONFLICT to "military conflict",
            ARMED_CONFLICT to "armed conflict",
            "Q15632617" to "fictional human",

            "P18" to "image",
            "P31" to "instance of",
            "P276" to "location",
            "P373" to "category",
            "P527" to "has parts",
            "P569" to "date of birth",
            "P570" to "date of death",
            "P580" to "start time",
            "P585" to "point in time",
            "P582" to "end time",
            "P710" to "participant",
            "P793" to "significant event",
            "P910" to "main category",
            "P1343" to "described by source",
            "P1344" to "participant in",
            "P1923" to "participating team",
        )
        
        val nameKeys = keyNames.map { it.value to it.key }.toMap()
    }

    fun englishName(): String {

        if (name.containsKey("en-gb"))
            return name["en-gb"]!!

        if (name.containsKey("en"))
            return name["en"]!!

        return name.values.first()
    }

    fun englishDescription(): String {

        if (description.containsKey("en-gb"))
            return description["en-gb"]!!

        if (description.containsKey("en"))
            return description["en"]!!

        return "??"
    }

    fun englishWikipediaPageName(): String? {

        if (!sitelinks.containsKey("enwiki"))
            return null

        val enwiki = sitelinks["enwiki"]!!

        if (enwiki.containsKey("title"))
            return enwiki["title"]!!

        return null
    }

    fun deathDate(): Int {
        return claims[DATE_OF_DEATH]!!.toInt()
    }

    fun hasLifeDates(): Boolean {
        return hasDate(DATE_OF_BIRTH) && hasDate(DATE_OF_DEATH)
    }

    private fun hasDate(claim: String): Boolean {
        val value = claims[claim]
        return !(value == null || value == UNKNOWN || value == "(no value)")
    }
    
    fun isInstanceOf(types: Set<String>): Boolean {
        val i = instanceOf()
        return types.contains(i)
    }

    fun isHuman(): Boolean {
        val i = instanceOf()
        return i == HUMAN
    }

    fun isFictionalHuman(): Boolean {
        val i = instanceOf()
        return i == FICTIONAL_HUMAN
    }

    fun isArmedConflict(): Boolean {
        val i = instanceOf()
        return i == ARMED_CONFLICT
    }
    
    fun wikipediaUrl(): String {
        return "https://en.wikipedia.org/wiki/$id"
    }

    fun participant(): String? {
        
        val participantKey = nameKeys["participant"]!!
        return claims[participantKey]
    }

    fun instanceOf(): String {
        return claims[INSTANCE_OF] ?: "??"
    }

    fun mainCategory(): String {
        return claims["910"] ?: "??"
    }

    fun category(): String {
        return claims["P373"] ?: "??"
    }

    override fun toString(): String {
        return englishName()
    }
}
