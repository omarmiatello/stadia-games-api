#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.4")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJsonOrNull
import com.github.omarmiatello.kotlinscripttoolbox.gson.writeJson
import com.github.omarmiatello.kotlinscripttoolbox.twitter.TwitterScope
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.exitProcess

val langRegex = "\\(.*?\\)".toRegex()
val pegiRegex = "(\\d+(?: \\(PROVISIONAL\\))?) ?(.*)".toRegex()
val dateParser = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun List<String>.toCleanListOrNull() =
    map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().takeIf { it.isNotEmpty() }

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?) {
    fun urlSlug() = url.takeLastWhile { it != '/' }
}

data class GameDetail(
    val title: String,
    val url: String,
    val img: String,
    val button: String?,
    val buttons: List<String>?,
    val description: String,
    val image_urls: List<String>?,
    val release_date: String?,
    val publisher: String?,
    val developer: String?,
    val languages: List<String>?,
    val available_country: List<String>?,
    val genre: List<String>?,
    val game_modes: List<String>?,
    val supported_input: List<String>?,
    val accessibility_features: List<String>?,
    val disclosure_details: List<String>?,
    val extra: Map<String, String>?,
    val notes: String?,
    val pegi_min_age: String?,
    val pegi_notes: List<String>?,
) {
    fun urlSlug() = url.takeLastWhile { it != '/' }

    fun isPro() = "Claim with Pro" in buttons.orEmpty()
    fun isUbisoftPlus() = "Get with Ubisoft+" in buttons.orEmpty()
    fun hasLocalCoop() = "Local co-op" in game_modes.orEmpty()
    fun hasLocalMultiplayer() = "Local multiplayer" in game_modes.orEmpty()
    fun hasDirectTouch() = "Touch support" in supported_input.orEmpty()

    fun toMarkdown() = buildString {
        append("[$title]($url)")
        if (button != null) append(" - $button")
        listOfNotNull(
            "#StadiaPro".takeIf { isPro() },
            "#UbisoftPlus".takeIf { isUbisoftPlus() },
        ).joinToString().also { services ->
            if (services.isNotEmpty()) append(" - available also with $services")
        }
        if (hasLocalCoop()) append(" #LocalCoop")
        if (hasLocalMultiplayer()) append(" #LocalMultiplayer")
        if (hasDirectTouch()) append(" #DirectTouch")
    }

    fun toPlainText(withButton: Boolean = true, showDetails: Boolean = true, withUrl: Boolean = true) = buildString {
        append(title)
        if (withButton && button != null) append(" - $button")
        listOfNotNull(
            "#StadiaPro".takeIf { isPro() },
            "#UbisoftPlus".takeIf { isUbisoftPlus() },
        ).joinToString().also { services ->
            if (showDetails && services.isNotEmpty()) append(" - available also with $services")
        }
        if (showDetails && hasLocalCoop()) append(" #LocalCoop")
        if (showDetails && hasLocalMultiplayer()) append(" #LocalMultiplayer")
        if (showDetails && hasDirectTouch()) append(" #DirectTouch")
        if (withUrl) append(" $url")
    }
}

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/")),
    scriptName = "Update for Stadia Games API",
) {
    val now = LocalDateTime.now()
    val changelogFilename = "changelog-${now.year}.md"
    // val changelogUrl = "https://github.com/omarmiatello/stadia-games-api/blob/main/data/$changelogFilename"
    val oldAllGames = readJsonOrNull<GameListResponse>("games.json")?.games

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

    // # Game Details API
    val allGameDetails = allGames.map { game ->
        readJsonOrNull<GameDetail>("detail/${game.urlSlug()}.json")
            ?: Jsoup.parse(URL(game.url), 10000)
                .parseGameDetail(game)
                .also { gameDetail -> writeJson("detail/${game.urlSlug()}.json", gameDetail) }
    }

    // # Update API (in `/data/` folder)
    writeJson("games.json", GameListResponse(api_version = 1, count = allGames.size, games = allGames))
    writeText("games.md", allGameDetails.toMarkdownNumberedList())

    // # Find old games
    if (oldAllGames != null) {
        // ## New games: allGames - oldAllGames = newGames
        // 1. Find new games
        val oldGamesTitles = oldAllGames.map { it.title }.toSet()
        val oldGamesUrls = oldAllGames.map { it.url }.toSet()
        val newGames = allGameDetails.filter { it.title !in oldGamesTitles && it.url !in oldGamesUrls }

        // 2. Send notifications
        newGames.toTelegramMessages(singular = "a new game", plural = "new games")
            .forEach { msg -> sendTelegramMessage(text = msg) }
        newGames.toTwitterMessages(singular = "A new game", plural = "new games")
            .forEach { msg -> sendTweet(msg, ignoreLimit = true) }

        // ## New demos: assuming `it.button != null` means "has demo"
        // 1. Find new demos
        val oldGamesDemosMap = oldAllGames
            .filter { it.button != null }
            .associate { it.title to it.button!! }
        val newGamesDemo = allGameDetails.filter { it.button != null && oldGamesDemosMap[it.title] != it.button }

        // 2. Send notifications
        val newGamesFiltered = newGamesDemo.filter { it !in newGames }      // to reduce noise
        newGamesFiltered.toTelegramMessages(singular = "a new demo", plural = "new demos")
            .forEach { msg -> sendTelegramMessage(text = msg) }
        newGamesFiltered.toTwitterMessages(singular = "A new demo", plural = "new demos")
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

// Set up: Games converter
fun List<GameDetail>.toTelegramMessages(singular: String, plural: String) = when (size) {
    0 -> emptyList()
    1 -> listOf("There is *$singular*: ${first().toMarkdown()}")
    in 2..5 -> listOf("There are *$size $plural*: ${first().toMarkdown()}") + drop(1).map { game -> game.toMarkdown() }
    else -> listOf("There are *$size $plural*: ${joinToString("\n") { it.toMarkdown() }}")
}

fun List<GameDetail>.toTwitterMessages(singular: String, plural: String) = when (size) {
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
        listOf(
            if (message.length <= maxLen) {
                "$message$suffix"
            } else {
                suffix = "...$suffix"
                maxLen = TwitterScope.MESSAGE_MAX_SIZE - suffix.length - TwitterScope.URL_MAX_SIZE
                "${message.take(maxLen)}$suffix"
            }
        )
    }
}

fun List<GameDetail>.toMarkdownNumberedList() =
    joinToString("\n") { game -> "1. ${game.toMarkdown()}" }

fun Document.parseGameDetail(
    game: Game,
): GameDetail {
    val data = select(".UyhU4c.n3017e")
        .mapNotNull {
            (it.select(".j35Pic").first()!!.text().lowercase() to it.ownText())
                .takeIf { it.second.isNotEmpty() }
        }
        .toMap().toMutableMap()
    println("open ${game.url}")
    val pegi = pegiRegex.find(select(".guztod").first()!!.text().trim().removePrefix("PEGI "))
        ?.groupValues.orEmpty()
    return GameDetail(
        title = select(".uJClu").first()!!.wholeText()
            .trim()
            .replace(" ", " "),
        url = game.url,
        img = game.img,
        button = game.button,
        buttons = select(".qoHRNc .wJYinb").map { it.text().replace(" Circular loading icon", "") }
            .takeIf { it.isNotEmpty() },
        description = select(".WzDfPb").first()!!.text().trim(),
        image_urls = select(".BmoOQe").map { it.attr("src") }.takeIf { it.isNotEmpty() },
        //bundles = gamesDoc.select("#SeeMore .om9tXc").map { it.text() }.takeIf { it.isNotEmpty() },
        release_date = data.remove("release date")?.let { dateFormatter.format(dateParser.parse(it)) },
        publisher = data.remove("publisher"),
        developer = data.remove("developer"),
        languages = data.remove("languages")?.replace(langRegex, "")?.split(",")?.toCleanListOrNull(),
        available_country = data.remove("available in")?.split(",")?.toCleanListOrNull(),
        genre = data.remove("genre")?.split(",")?.toCleanListOrNull(),
        game_modes = data.remove("game modes")?.split(",")?.toCleanListOrNull(),
        supported_input = data.remove("supported input")?.split(",")?.toCleanListOrNull(),
        accessibility_features = data.remove("accessibility features")?.split(",")?.toCleanListOrNull(),
        disclosure_details = data.remove("disclosure details")?.split(",")?.toCleanListOrNull(),
        extra = data.takeIf { it.isNotEmpty() },
        notes = select(".Tykkac .MZ4wg").first()?.text()?.trim(),
        pegi_min_age = pegi.getOrNull(1),
        pegi_notes = pegi.getOrNull(2)?.split(",")?.toCleanListOrNull(),
    )
}

exitProcess(status = 0)
