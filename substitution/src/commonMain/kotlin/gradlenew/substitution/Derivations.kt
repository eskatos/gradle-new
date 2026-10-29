package gradlenew.substitution

object Derivations {

    fun toCamelCase(name: String): String {
        val builder = StringBuilder()
        var position = 0
        while (position < name.length) {
            val separatorStart = indexOfSeparator(name, position)
            if (separatorStart < 0) break
            var separatorEnd = separatorStart
            while (separatorEnd < name.length && !isWordCharacter(name[separatorEnd])) separatorEnd++
            builder.append(capitalize(name.substring(position, separatorStart)))
            position = separatorEnd
        }
        builder.append(capitalize(name.substring(position)))
        return builder.toString()
    }

    fun toNamespace(name: String): String {
        val result = StringBuilder()
        var position = 0
        while (position < name.length) {
            while (position < name.length && !isIdentifierStart(name[position])) position++
            if (position == name.length) break
            if (result.isNotEmpty()) result.append('_')
            result.append(name[position++])
            while (position < name.length && isIdentifierPart(name[position])) result.append(name[position++])
        }
        return result.toString()
    }

    fun capitalize(text: String): String =
        if (text.isEmpty()) text else text[0].titlecaseChar() + text.substring(1)

    private fun indexOfSeparator(name: String, from: Int): Int {
        for (index in from until name.length) {
            if (!isWordCharacter(name[index])) return index
        }
        return -1
    }

    private fun isWordCharacter(char: Char) = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '_'

    private fun isIdentifierStart(char: Char) = char.isLetter() || char == '_'

    private fun isIdentifierPart(char: Char) = char.isLetter() || char.isDigit() || char == '_'
}
