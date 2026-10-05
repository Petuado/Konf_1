package ru.vfsshell

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultCaret

private const val VFS_NAME = "VFS-Shell"
private const val WINDOW_WIDTH = 900
private const val WINDOW_HEIGHT = 560
private const val TERM_FONT_SIZE = 14
private const val TERM_FONT_NAME = "Menlo"
private const val COLOR_BG_RGB = 0x000000
private const val COLOR_FG_RGB = 0xD0D0D0
private const val EXIT_OK = true
private const val EXIT_FAIL = false
private const val MAX_CD_ARGS = 1

private val COLOR_BG = Color(COLOR_BG_RGB)
private val COLOR_FG = Color(COLOR_FG_RGB)
private val TERM_FONT = Font(TERM_FONT_NAME, Font.PLAIN, TERM_FONT_SIZE)

/**
 * Конфигурация запуска эмулятора.
 *
 * @property vfsPath путь к VFS.
 * @property startupScript путь к стартовому скрипту или null.
 * @property debug отладочный вывод при старте.
 */
data class Config(
    val vfsPath: Path,
    val startupScript: Path?,
    val debug: Boolean = true,
)

/** Контекст, передаваемый командам при выполнении. */
interface ShellContext {
    val config: Config
    fun println(text: String)
    fun requestExit()
}

/** Команда эмулятора. */
interface Command {
    val name: String
    fun execute(args: List<String>, ctx: ShellContext): Boolean
}

/** Заглушка `ls`. */
class LsCommand : Command {
    override val name = "ls"
    override fun execute(args: List<String>, ctx: ShellContext): Boolean {
        ctx.println("ls: имя='ls', аргументы=$args")
        return EXIT_OK
    }
}

/** Заглушка `cd`. */
class CdCommand : Command {
    override val name = "cd"
    override fun execute(args: List<String>, ctx: ShellContext): Boolean {
        if (args.size > MAX_CD_ARGS) {
            ctx.println("cd: слишком много аргументов")
            return EXIT_FAIL
        }
        ctx.println("cd: имя='cd', аргументы=$args")
        return EXIT_OK
    }
}

/** `exit` — завершает эмулятор. */
class ExitCommand : Command {
    override val name = "exit"
    override fun execute(args: List<String>, ctx: ShellContext): Boolean {
        if (args.isNotEmpty()) {
            ctx.println("exit: аргументы не поддерживаются")
            return EXIT_FAIL
        }
        ctx.requestExit()
        return EXIT_OK
    }
}

/** Реестр команд. */
class CommandRegistry(commands: List<Command>) {
    private val byName: Map<String, Command> = commands.associateBy { it.name }

    /** Ищет команду по имени. */
    fun find(name: String): Command? = byName[name]

    /** Имена всех команд. */
    fun names(): Set<String> = byName.keys

    companion object {
        /** Стандартный набор команд. */
        fun default() = CommandRegistry(
            listOf(LsCommand(), CdCommand(), ExitCommand()),
        )
    }
}

/**
 * REPL-эмулятор на Swing.
 *
 * @property config конфигурация запуска.
 */
class ShellRepl(override val config: Config) : ShellContext {

    /** Флаг запроса на выход. */
    var exitRequested: Boolean = false
        private set

    private val parser = Parser()
    private val registry = CommandRegistry.default()
    private val frame = JFrame("$VFS_NAME — ${config.vfsPath}")
    private val terminal = JTextArea()
    private val history = mutableListOf<String>()
    private var historyIndex = 0
    private var inputStart = 0
    private val prompt: String get() = "${config.vfsPath} $ "

    override fun println(text: String) = append(text + "\n")

    override fun requestExit() {
        exitRequested = true
        frame.dispose()
    }

    /** Печатает ввод как эхо. */
    fun echoInput(line: String) = println("$prompt$line")

    /** Запускает окно. */
    fun start() = SwingUtilities.invokeLater { buildAndShow() }

    private fun buildAndShow() {
        setupFrame()
        setupTerminal()
        if (config.debug) printDebugInfo()
        initVfs()
        config.startupScript?.let { StartupScriptRunner(this).run(it) }
        appendPrompt()
    }

    private fun setupFrame() {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT)
        frame.layout = BorderLayout()
    }

    private fun setupTerminal() {
        terminal.isEditable = true
        terminal.background = COLOR_BG
        terminal.foreground = COLOR_FG
        terminal.caretColor = COLOR_FG
        terminal.font = TERM_FONT
        terminal.lineWrap = true
        terminal.wrapStyleWord = true
        (terminal.caret as DefaultCaret).updatePolicy = DefaultCaret.ALWAYS_UPDATE
        terminal.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) = handleKey(e)
        })
        frame.add(JScrollPane(terminal), BorderLayout.CENTER)
        frame.isVisible = true
        terminal.requestFocusInWindow()
    }

    private fun printDebugInfo() {
        append("=== Параметры запуска ===\n")
        append("VFS:     ${config.vfsPath}\n")
        append("Script:  ${config.startupScript ?: "(не задан)"}\n")
        append("OS cwd:  ${System.getProperty("user.dir")}\n")
        append("OS home: ${System.getProperty("user.home")}\n")
        append("JVM:     ${System.getProperty("java.version")}\n")
        append("=========================\n")
    }

    private fun initVfs() {
        try {
            config.vfsPath.toFile().mkdirs()
            append("[vfs] корень VFS: ${config.vfsPath}\n")
        } catch (e: SecurityException) {
            append("[vfs] не удалось создать VFS: ${e.message}\n")
        }
    }

    private fun append(text: String) {
        terminal.append(text)
        terminal.caretPosition = terminal.document.length
    }

    private fun appendPrompt() {
        terminal.append(prompt)
        inputStart = terminal.document.length
        terminal.caretPosition = terminal.document.length
    }

    private fun currentInput(): String {
        val doc = terminal.document
        return try {
            doc.getText(inputStart, doc.length - inputStart)
        } catch (e: BadLocationException) {
            ""
        }
    }

    private fun replaceInput(text: String) {
        val doc = terminal.document
        try {
            doc.remove(inputStart, doc.length - inputStart)
            doc.insertString(inputStart, text, null)
        } catch (e: BadLocationException) {

        }
        terminal.caretPosition = terminal.document.length
    }

    private fun handleKey(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_ENTER -> { e.consume(); onEnter() }
            KeyEvent.VK_UP -> { e.consume(); onHistoryUp() }
            KeyEvent.VK_DOWN -> { e.consume(); onHistoryDown() }
            KeyEvent.VK_LEFT -> if (atStart()) e.consume()
            KeyEvent.VK_BACK_SPACE -> if (atStart()) e.consume()
            else -> if (terminal.caretPosition < inputStart) {
                terminal.caretPosition = terminal.document.length
            }
        }
    }

    private fun atStart(): Boolean = terminal.caretPosition <= inputStart

    private fun onEnter() {
        val line = currentInput()
        append("\n")
        if (line.isNotBlank()) {
            history.add(line)
            historyIndex = history.size
            execute(line)
        }
        if (!exitRequested) appendPrompt()
    }

    private fun onHistoryUp() {
        if (history.isEmpty()) return
        if (historyIndex > 0) historyIndex--
        replaceInput(history[historyIndex])
    }

    private fun onHistoryDown() {
        if (history.isEmpty()) return
        if (historyIndex < history.size - 1) {
            historyIndex++
            replaceInput(history[historyIndex])
        } else {
            historyIndex = history.size
            replaceInput("")
        }
    }

    /** Выполняет строку команды */
    fun execute(line: String): Boolean {
        val tokens = parseOrReport(line) ?: return EXIT_FAIL
        if (tokens.isEmpty()) return EXIT_OK
        return dispatch(tokens)
    }

    private fun parseOrReport(line: String): List<String>? =
        try {
            parser.parse(line)
        } catch (e: ParseException) {
            println("shell: ${e.message}"); null
        }

    private fun dispatch(tokens: List<String>): Boolean {
        val name = tokens.first()
        val args = tokens.drop(1)
        val command = registry.find(name)
        if (command == null) {
            if (args.isEmpty() && looksLikeEnvExpansion(name)) {
                println(name)
                return EXIT_OK
            }
            println("shell: команда не найдена: $name")
            return EXIT_FAIL
        }
        return runSafely(command, args)
    }

    /**
     * Эвристика: считаем, что токен — результат раскрытия переменной
     * окружения, если он содержит разделитель пути или совпадает с одной
     * из переменных окружения ОС.
     */
    private fun looksLikeEnvExpansion(token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.startsWith("/") || token.startsWith("~")) return true
        if (token.contains('/') || token.contains('\\')) return true
        return System.getenv().containsValue(token)
    }

    private fun runSafely(command: Command, args: List<String>): Boolean =
        try {
            command.execute(args, this)
        } catch (e: Exception) {
            println("shell: ошибка '${command.name}': ${e.message}")
            EXIT_FAIL
        }
}

/**
 * Выполняет стартовый скрипт: построчно, с эхо, ошибки пропускает.
 *
 * @property shell REPL, в который пишем и чьи команды вызываем.
 */
class StartupScriptRunner(private val shell: ShellRepl) {

    /** Запускает скрипт из файла. */
    fun run(scriptPath: Path) {
        shell.println("[script] выполняю: $scriptPath")
        if (!Files.isRegularFile(scriptPath)) {
            shell.println("[script] файл не найден: $scriptPath")
            return
        }
        Files.readAllLines(scriptPath).forEach { runSingle(it) }
        shell.println("[script] завершён")
    }

    private fun runSingle(rawLine: String) {
        val line = rawLine.trimEnd()
        val stripped = line.trim()
        if (stripped.isEmpty() || stripped.startsWith("#")) return
        shell.echoInput(line)
        shell.execute(line)
    }
}