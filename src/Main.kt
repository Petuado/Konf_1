package ru.vfsshell

import java.nio.file.Paths

private const val DEFAULT_VFS_DIR = "vfs_root"

/**
 * Разбирает аргументы командной строки.
 *
 * @param args массив аргументов, как пришёл из `main`.
 * @return готовая конфигурация запуска.
 */
fun parseCli(args: Array<String>): Config {
    val vfs = optionValue(args, "--vfs") ?: defaultVfsPath()
    val script = optionValue(args, "--script")?.let { Paths.get(it) }
    return Config(
        vfsPath = Paths.get(vfs).toAbsolutePath().normalize(),
        startupScript = script?.toAbsolutePath()?.normalize(),
        debug = !args.contains("--no-debug"),
    )
}

/** Возвращает значение опции `--name value` или null. */
private fun optionValue(args: Array<String>, name: String): String? {
    val index = args.indexOf(name)
    if (index < 0) return null
    return args.getOrNull(index + 1)
}

/** Путь VFS по умолчанию: `~/vfs_root`. */
private fun defaultVfsPath(): String {
    val home = System.getProperty("user.home") ?: "."
    return Paths.get(home, DEFAULT_VFS_DIR).toString()
}

/** true, если пользователь запросил справку. */
fun helpRequested(args: Array<String>): Boolean =
    args.contains("-h") || args.contains("--help")

/** Печатает справку. */
fun printHelp() {
    println(
        """
        vfs-shell — эмулятор UNIX-подобной оболочки (этап 2)

        Использование:
          vfs-shell [--vfs PATH] [--script FILE] [--no-debug]

        Параметры:
          --vfs PATH     путь к VFS
          --script FILE  путь к стартовому скрипту
          --no-debug     отключить отладочный вывод
          -h, --help     справка
        """.trimIndent(),
    )
}

/** Точка входа. */
fun main(args: Array<String>) {
    if (helpRequested(args)) {
        printHelp()
        return
    }
    val config = parseCli(args)
    ShellRepl(config).start()
}