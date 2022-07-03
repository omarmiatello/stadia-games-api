#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.3")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJsonOrNull
import com.github.omarmiatello.kotlinscripttoolbox.gson.writeJson
import com.github.omarmiatello.kotlinscripttoolbox.twitter.TwitterScope
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import org.jsoup.Jsoup
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?) {
    fun toMarkdown() = buildString {
        append("[$title]($url)")
        if (button != null) append(" - $button")
    }

    fun toPlainText(withButton: Boolean = true, withUrl: Boolean = true) = buildString {
        append(title)
        if (withButton && button != null) append(" - $button")
        if (withUrl) append(" $url")
    }
}

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.fromDefaults(filepathPrefix = "data/")),
    scriptName = "Update for Stadia Games API",
) {
    val now = LocalDateTime.now()
    val changelogFilename = "changelog-${now.year}.md"
    val changelogUrl = "https://github.com/omarmiatello/stadia-games-api/blob/main/data/$changelogFilename"
    val oldAllGames = readJsonOrNull<GameListResponse>("games.json")?.games

    // Set up: Games converter
    fun List<Game>.toTelegramMessages(singular: String, plural: String) = when (size) {
        0 -> emptyList()
        1 -> listOf("There is *$singular*: ${first().toMarkdown()}")
        in 2..5 -> listOf("There are *$size $plural*: ${first().toMarkdown()}") + drop(1).map { game -> game.toMarkdown() }
        else -> listOf("There are *$size $plural*: ${joinToString("\n") { it.toMarkdown() }}")
    }

    fun List<Game>.toTwitterMessages(singular: String, plural: String) = when (size) {
        0 -> emptyList()
        1 -> listOf("$singular on #Stadia\n\n${first().toPlainText()}")
        in 2..5 -> map { game -> "$size $plural on #Stadia, including\n\n${game.toPlainText()}" }
        else -> {
            val message = "$size $plural on #Stadia\n${
                groupBy({ it.button }, { it.toPlainText(withButton = false, withUrl = false) })
                    .toList()
                    .joinToString("\n") { (button, games) ->
                        buildString {
                            if (button != null) appendLine("$button:") else appendLine()
                            append(games.joinToString("\n"))
                        }
                    }
            }"
            var suffix = "\nFull changelog: "
            var maxLen = TwitterScope.MESSAGE_MAX_SIZE - suffix.length - TwitterScope.URL_MAX_SIZE
            listOf(if (message.length <= maxLen) {
                "$message$suffix$changelogUrl"
            } else {
                suffix = "...$suffix"
                maxLen = TwitterScope.MESSAGE_MAX_SIZE - suffix.length - TwitterScope.URL_MAX_SIZE
                "${message.take(maxLen)}$suffix$changelogUrl"
            })
        }
    }

    fun List<Game>.toMarkdownNumberedList() =
        joinToString("\n") { game -> "1. ${game.toMarkdown()}" }

    // # Parse games from https://stadia.google.com/games
    val gamesDoc = Jsoup.parse(URL("https://stadia.google.com/games"), 10000)
    val allGames = gamesDoc.select(".tLwy5")
        .map { gameDoc ->
            Game(
                title = gameDoc.select(".Oou9nd").first()!!.wholeText()
                    .trim()
                    .replace(" ", " "),
                url = "https://stadia.google.com/" + gameDoc.select(".nBACW.QAAyWd.wJYinb").first()!!
                    .attr("href"),
                img = gameDoc.select(".EvMdmf").first()!!.attr("src"),
                button = gameDoc.select(".Pyflbb").first()?.wholeText(),
            )
        }
        .distinctBy { it.title }
        .sortedBy { it.title }

    // # Update API (in `/data/` folder)
    writeJson("games.json", GameListResponse(api_version = 1, count = allGames.size, games = allGames))
    writeText("games.md", allGames.toMarkdownNumberedList())

    // # Find old games
    if (oldAllGames != null) {
        // ## New games: allGames - oldAllGames = newGames
        // 1. Find new games
        val newGames = oldAllGames
            .map { it.title }
            .toSet()
            .let { oldGamesTitles -> allGames.filter { it.title !in oldGamesTitles } }

        // 2. Send notifications
        newGames.toTelegramMessages(singular = "a new game", plural = "new games")
            .forEach { msg -> sendTelegramMessage(text = msg) }
        newGames.toTwitterMessages(singular = "a new game", plural = "new games")
            .forEach { msg -> sendTweet(msg, ignoreLimit = true) }

        // ## New demos: assuming `it.button != null` means "has demo"
        // 1. Find new demos
        val newGamesDemo = oldAllGames
            .filter { it.button != null }
            .associate { it.title to it.button!! }
            .let { oldGamesDemosMap -> allGames.filter { it.button != null && oldGamesDemosMap[it.title] != it.button } }

        // 2. Send notifications
        val newGamesFiltered = newGamesDemo.filter { it !in newGames }      // to reduce noise
        newGamesFiltered.toTelegramMessages(singular = "a new demo", plural = "new demos")
            .forEach { msg -> sendTelegramMessage(text = msg) }
        newGamesFiltered.toTwitterMessages(singular = "a new demo", plural = "new demos")
            .forEach { msg -> sendTweet(msg, ignoreLimit = true) }

        // ## Update changelog (in `/data/` folder)
        if (newGames.isNotEmpty() || newGamesDemo.isNotEmpty()) {
            writeText(
                pathname = changelogFilename,
                text = buildString {
                    appendLine("## ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}")
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

                    val oldHistory = readTextOrNull(pathname = changelogFilename)
                    if (oldHistory != null) append(oldHistory)
                }
            )
        }
    }
}

exitProcess(status = 0)
