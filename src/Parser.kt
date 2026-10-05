package ru.vfsshell

private val ENV_VAR_START = '$'

/** Ошибка разбора строки команды. */
class ParseException(message: String) : Exception(message)

/**
 * Парсер строки команды: раскрытие переменных окружения и токенизация.
 *
 * @property env карта переменных окружения (по умолчанию — системные).
 */
class Parser(private val env: Map<String, String> = System.getenv()) {

    /** Разбирает строку на токены, раскрывая `$VAR` и `${VAR}`. */
    fun parse(line: String): List<String> =
        tokenize(expandEnvVars(line))

    /** Раскрывает переменные окружения в строке. */
    fun expandEnvVars(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            i = if (text[i] == ENV_VAR_START) {
                i + appendVar(text, i, out)
            } else {
                out.append(text[i]); i + 1
            }
        }
        return out.toString()
    }

    /** Поглощает одну переменную, возвращает число съеденных символов. */
    private fun appendVar(text: String, start: Int, out: StringBuilder): Int {
        val next = text.getOrNull(start + 1)
        if (next == null) { out.append(ENV_VAR_START); return 1 }
        if (next == '{') return appendBraced(text, start, out)
        if (next.isLetter() || next == '_') return appendPlain(text, start, out)
        out.append(ENV_VAR_START); return 1
    }

    /** Раскрывает форму `${VAR}`. */
    private fun appendBraced(text: String, start: Int, out: StringBuilder): Int {
        val end = text.indexOf('}', start + 2)
        if (end < 0) { out.append(text.substring(start)); return text.length - start }
        out.append(env[text.substring(start + 2, end)] ?: "")
        return end - start + 1
    }

    /** Раскрывает форму `$VAR`. */
    private fun appendPlain(text: String, start: Int, out: StringBuilder): Int {
        var j = start + 1
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
        out.append(env[text.substring(start + 1, j)] ?: "")
        return j - start
    }

    /** Токенизирует строку с учётом одинарных и двойных кавычек. */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var started = false
        for (ch in text) {
            when {
                ch == '\'' && !inDouble -> { inSingle = !inSingle; started = true }
                ch == '"' && !inSingle -> { inDouble = !inDouble; started = true }
                ch.isWhitespace() && !inSingle && !inDouble -> {
                    if (started) { tokens.add(current.toString()); current.clear(); started = false }
                }
                else -> { current.append(ch); started = true }
            }
        }
        if (inSingle || inDouble) throw ParseException("незакрытая кавычка")
        if (started) tokens.add(current.toString())
        return tokens
    }
}