package vfs

/**
 * Парсер командной строки и раскрытие переменных окружения.
 *
 * Поддерживаются формы: $VAR, ${VAR}, экранирование \$VAR.
 * Переменные берутся из реального окружения ОС (System.getenv).
 */
object Parser {

    private const val ESCAPED_DOLLAR = '$'

    /**
     * Раскрывает переменные окружения в строке.
     *
     * @param text исходная строка пользователя
     * @param env  функция доступа к переменной окружения (по умолчанию System.getenv)
     * @return строка с раскрытыми переменными
     */
    fun expandEnvVars(
        text: String,
        env: (String) -> String? = { System.getenv(it) }
    ): String {
        val sb = StringBuilder(text.length)
        var i = 0

        while (i < text.length) {
            val c = text[i]

            when {
                c == '\\' && i + 1 < text.length && text[i + 1] == '$' -> {
                    sb.append(ESCAPED_DOLLAR)
                    i += 2
                }

                c == '$' -> {
                    val consumed = appendVariable(text, i, sb, env)
                    if (consumed == 0) {
                        sb.append(c)
                        i++
                    } else {
                        i += consumed
                    }
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /**
     * Пытается прочитать переменную начиная с позиции '$' (индекс [start]) и
     * записать её значение в [sb].
     *
     * @return количество съеденных символов исходной строки (включая '$');
     *         0 — если после '$' переменной нет.
     */
    private fun appendVariable(
        text: String,
        start: Int,
        sb: StringBuilder,
        env: (String) -> String?
    ): Int {
        val braceResult = readBracedVar(text, start)
        if (braceResult != null) {
            val (name, length) = braceResult
            sb.append(env(name) ?: "\${$name}")
            return length
        }

        val namedResult = readNamedVar(text, start)
        if (namedResult != null) {
            val (name, length) = namedResult
            sb.append(env(name) ?: "\$$name")
            return length
        }

        return 0
    }

    /**
     * Читает форму ${NAME}.
     *
     * @return пара (имя, длина) или null, если форма не распознана.
     */
    private fun readBracedVar(text: String, start: Int): Pair<String, Int>? {
        if (start + 1 >= text.length || text[start + 1] != '{') return null

        val end = text.indexOf('}', start + 2)
        if (end == -1) return null

        val name = text.substring(start + 2, end)
        return name to (end - start + 1)
    }
    /**
     * Читает форму $NAME.
     *
     * @return пара (имя, длина) или null, если имя не начинается корректно.
     */
    private fun readNamedVar(text: String, start: Int): Pair<String, Int>? {
        if (start + 1 >= text.length) return null
        if (!isNameStart(text[start + 1])) return null

        var j = start + 2
        while (j < text.length && isNamePart(text[j])) j++

        val name = text.substring(start + 1, j)
        return name to (j - start)
    }

    private fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_'

    private fun isNamePart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}