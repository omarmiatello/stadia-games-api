#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.4")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJson
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import java.io.File
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String?, val img: String, val button: String?) {
    fun toMarkdown() = buildString {
        if (url != null) {
            append("[$title]($url)")
        } else {
            append(title)
        }
        if (button != null) append(" - $button")
    }
}

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/wayback-machine/")),
) {

    fun List<Game>.toMarkdownNumberedList() =
        joinToString("\n") { game -> "1. ${game.toMarkdown()}" }

    val dir = File("data/wayback-machine")
    dir.walkTopDown().filter { it.name == ".DS_Store" }.forEach { it.delete() }

    val sortedFiles =
        dir.walkTopDown().filter { it.isFile }.toList().map { it.name }.sorted()

    val nameToUrls = readJson<GameListResponse>(sortedFiles.last()).games.associate { it.title to it.url }
    var oldAllGames = readJson<GameListResponse>(sortedFiles.first()).games.map {
        it.copy(url = it.url ?: nameToUrls[it.title])
    }

    val firstYear = sortedFiles.first().take(4)
    val firstMonth = sortedFiles.first().drop(4).take(2)
    val firstDay = sortedFiles.first().drop(6).take(2)

    writeText(
        pathname = "changelog-$firstYear.md",
        text = buildString {
            appendLine("## $firstYear-$firstMonth-$firstDay (from Wayback Machine)")
            appendLine()
            append("### ")
            appendLine("${oldAllGames.size} new games")
            appendLine(oldAllGames.toMarkdownNumberedList())
            appendLine()
        }
    )

    sortedFiles.forEach skip@{ filename ->
        val year = filename.take(4)
        val month = filename.drop(4).take(2)
        val day = filename.drop(6).take(2)
        val allGames = readJson<GameListResponse>(filename).games.map {
            it.copy(
                url = it.url ?: nameToUrls[it.title],
                button = it.button?.let {
                    it
                        .replace("min free", "min")
                        .replace("Play 30", "Play for 30")
                        .replace("Play 60", "Play for 60")
                        .replace("Play 120", "Play for 120")
                },
            )
        }

        val rulesForSkip: List<Pair<String, (Game) -> Boolean>> = listOf(
            "A.O.T." to { it.title.startsWith("A.O.T.") },
            "Zahrajte si" to { it.button?.startsWith("Zahrajte si") ?: false },
            "Hrajte" to { it.button?.startsWith("Hrajte") ?: false },
            "zakleté království" to { it.title.endsWith("zakleté království") },
            "Edizione Gold" to { it.title.endsWith("Edizione Gold") },
            "A Familía Addams" to { it.title.startsWith("A Familía Addams") },
        )
        rulesForSkip.forEach { (name, rule) ->
            if (allGames.any(rule)) {
                println("$filename SKIPPED for $name")
                return@skip
            }
        }

        val newGames = oldAllGames
            .map { it.title }
            .toSet()
            .let { oldGamesTitles -> allGames.filter { it.title !in oldGamesTitles } }

        val newGamesDemo = oldAllGames
            .filter { it.button != null }
            .associate { it.title to it.button!! }
            .let { oldGamesDemosMap -> allGames.filter { it.button != null && oldGamesDemosMap[it.title] != it.button } }

        if (newGames.isNotEmpty() || newGamesDemo.isNotEmpty()) {
            writeText(
                pathname = "changelog-$year.md",
                text = buildString {
                    appendLine("## $year-$month-$day (from Wayback Machine)")
                    if (newGames.isNotEmpty()) {
                        appendLine()
                        append("### ")
                        appendLine(if (newGames.size == 1) "1 new game" else "${newGames.size} new games")
                        appendLine(newGames.toMarkdownNumberedList())
                    }
                    if (newGamesDemo.isNotEmpty()) {
                        appendLine()
                        append("### ")
                        appendLine(if (newGamesDemo.size == 1) "1 new demo" else "${newGamesDemo.size} new demos")
                        appendLine(newGamesDemo.toMarkdownNumberedList())
                    }
                    appendLine()

                    val oldHistory = readTextOrNull(pathname = "changelog-$year.md")
                    if (oldHistory != null) append(oldHistory)
                }
            )
        }

        if (newGames.isNotEmpty()) {
            println("$filename ${newGames.size} newGames: " + newGames.joinToString { "${it.title} (${it.button})" })
        }
        if (newGamesDemo.isNotEmpty()) {
            println("$filename ${newGamesDemo.size} newGamesDemo: " + newGamesDemo.joinToString { "${it.title} (${it.button})" })
        }
        oldAllGames = allGames
    }
}

exitProcess(status = 0)
