package com.spendlens.app.domain

/** Spending categories. Colors are ARGB so the domain layer stays free of Compose. */
enum class Category(val key: String, val label: String, val argb: Long) {
    FOOD("food","Food & Dining", 0xFFFF7A59),
    GROCERIES("groceries","Groceries", 0xFF2FC98A),
    SHOPPING("shopping","Shopping", 0xFFB26BFF),
    TRANSPORT("transport","Transport", 0xFF3FA7FF),
    BILLS("bills","Bills & Utilities", 0xFFFFBE3D),
    ENTERTAINMENT("entertainment","Entertainment", 0xFFFF5C8A),
    HEALTH("health","Health", 0xFF3FD4C4),
    TRAVEL("travel","Travel", 0xFF5B7CFF),
    EDUCATION("education","Education", 0xFF8BD450),
    HOUSING("housing","Rent & Home", 0xFFE8915B),
    TRANSFERS("transfers","Transfers", 0xFF9AA3B5),
    OTHER("other","Other", 0xFF7D8597);

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** Keyword based classifier: good enough to pre-fill, and the user can always change it. */
object CategoryClassifier {

    private val rules: List<Pair<Category, List<String>>> = listOf(
        Category.FOOD to listOf(
            "swiggy", "zomato", "uber eats", "restaurant", "cafe", "café", "coffee", "starbucks", "domino",
            "pizza", "mcdonald", "kfc", "burger", "bakery", "biryani", "chai", "tea", "juice", "subway",
            "dunkin", "eatery", "dhaba", "kitchen", "food", "foods", "dine", "diner", "canteen", "haldiram",
            "barbeque", "eatsure", "box8", "faasos", "chaayos", "third wave", "blue tokai", "baskin", "sweets",
        ),
        Category.GROCERIES to listOf(
            "bigbasket", "big basket", "blinkit", "zepto", "instamart", "dmart", "d-mart", "grocery",
            "grocer", "supermarket", "super market", "reliance fresh", "jiomart", "more retail", "kirana",
            "vegetable", "fruits", "dairy", "milk", "nature's basket", "spar", "star bazaar", "walmart",
            "costco", "whole foods", "trader joe", "aldi", "tesco", "provisions", "general store",
        ),
        Category.TRANSPORT to listOf(
            "uber", "ola", "olacabs", "ola cabs", "rapido", "metro", "petrol", "diesel", "fuel",
            "indian oil", "iocl", "bharat petroleum", "bpcl", "hpcl", "shell", "parking", "fastag", "toll",
            "cab", "cabs", "taxi", "auto rickshaw", "redbus", "bus", "lyft", "namma yatri", "blusmart",
            "irctc", "railway", "service station", "filling station", "ev charging",
        ),
        Category.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "decathlon", "ikea",
            "croma", "reliance digital", "vijay sales", "lifestyle", "shoppers stop", "westside", "zara",
            "h&m", "uniqlo", "pantaloons", "max fashion", "store", "shop", "mall", "mart", "boutique",
            "electronics", "footwear", "fashion", "retail", "target", "best buy", "ebay",
        ),
        Category.BILLS to listOf(
            "electricity", "bescom", "tneb", "tangedco", "msedcl", "tata power", "bses", "water", "gas",
            "indane", "bharat gas", "broadband", "airtel", "jio", "vodafone", "vi", "bsnl", "recharge",
            "dth", "tata play", "postpaid", "prepaid", "bill", "bills", "act fibernet", "hathway",
            "insurance", "lic", "premium", "emi", "loan", "credit card", "utility", "municipal",
        ),
        Category.ENTERTAINMENT to listOf(
            "netflix", "spotify", "prime video", "hotstar", "jiocinema", "bookmyshow", "book my show",
            "pvr", "inox", "cinepolis", "youtube", "gaming", "steam", "playstation", "xbox", "movie",
            "cinema", "concert", "sonyliv", "zee5", "apple music", "disney", "hbo", "theatre",
        ),
        Category.HEALTH to listOf(
            "pharmacy", "pharma", "apollo", "medplus", "hospital", "clinic", "doctor", "dr", "1mg",
            "pharmeasy", "netmeds", "diagnostic", "lab", "labs", "health", "medical", "dental", "medico",
            "chemist", "wellness", "gym", "fitness", "cult.fit", "cultfit", "yoga",
        ),
        Category.TRAVEL to listOf(
            "makemytrip", "make my trip", "goibibo", "airbnb", "oyo", "hotel", "resort", "indigo",
            "air india", "vistara", "spicejet", "akasa", "cleartrip", "booking.com", "flight", "yatra",
            "ixigo", "easemytrip", "airline", "airways", "expedia", "travels", "tours",
        ),
        Category.EDUCATION to listOf(
            "school", "college", "university", "tuition", "udemy", "coursera", "byju", "unacademy",
            "course", "academy", "institute", "books", "stationery", "exam", "fees",
        ),
        Category.HOUSING to listOf(
            "rent", "rental", "maintenance", "society", "landlord", "apartment", "housing", "nobroker",
            "furniture", "plumber", "electrician", "urban company", "urbanclap", "home services", "repair",
        ),
    )

    private val compiled: List<Pair<Category, Regex>> = rules.map { (category, words) ->
        category to Regex(words.joinToString("|") { keywordPattern(it) })
    }

    /** Short keywords must be whole words ("vi" must not match "ravi"); longer ones may be prefixes. */
    private fun keywordPattern(keyword: String): String {
        val escaped = Regex.escape(keyword)
        return if (keyword.length <= 4) "(?<![a-z0-9])$escaped(?![a-z0-9])" else "(?<![a-z0-9])$escaped"
    }

    private val transferHints = listOf("sent to", "transfer", "money sent", "to self", "p2p")
    private val upiId = Regex("[a-z0-9._-]{2,}@[a-z]{2,}")

    fun classify(merchant: String?, rawText: String? = null): Category {
        match(merchant.orEmpty().lowercase())?.let { return it }
        val text = rawText.orEmpty().lowercase()
        // UPI handles such as "zomato-order@ptybl" usually name the merchant even when the payee name is odd.
        upiId.findAll(text).forEach { handle -> match(handle.value.substringBefore('@').replace('.', ' '))?.let { return it } }
        if (looksLikePerson(merchant) && (transferHints.any { it in text } || "upi" in text)) return Category.TRANSFERS
        return Category.OTHER
    }

    private fun match(haystack: String): Category? {
        if (haystack.isBlank()) return null
        return compiled.firstOrNull { (_, regex) -> regex.containsMatchIn(haystack) }?.first
    }

    /** "Priya Sharma" looks like a person, "SWIGGY LIMITED" doesn't. */
    private fun looksLikePerson(merchant: String?): Boolean {
        val words = merchant?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: return false
        if (words.size !in 2..4) return false
        val businessWords = setOf(
            "ltd", "limited", "pvt", "private", "llp", "inc", "store", "shop", "services", "enterprises",
            "traders", "company", "co", "corp", "mart", "foods", "solutions", "technologies", "india",
        )
        return words.all { w -> w.all { it.isLetter() || it == '.' } && w.lowercase().trimEnd('.') !in businessWords }
    }
}
