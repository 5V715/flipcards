package flipcards.play

private val combiningMarks = Regex("[\\u0300-\\u036f]")

// NFD splits "á" into "a" plus a combining accent, which is then removed.
actual fun stripAccents(text: String): String =
    (text.asDynamic().normalize("NFD") as String).replace(combiningMarks, "")
