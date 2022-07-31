#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.5")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.*
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJsonOrNull
import com.github.omarmiatello.kotlinscripttoolbox.gson.writeJson
import com.github.omarmiatello.kotlinscripttoolbox.twitter.twitterUrl
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import com.github.omarmiatello.telegram.ParseMode
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
val now = LocalDateTime.now()
val isSilentNotification = now.hour in 0..7
val changelogFilename = "changelog-${now.year}.md"
val changelogUrl = "https://github.com/omarmiatello/stadia-games-api/blob/main/data/$changelogFilename"
val statsUrl = "https://github.com/omarmiatello/stadia-games-api/blob/main/data/stats.md"
val milestones = listOf(5, 10, 20, 50, 100,
    150, 200, 250, 300, 350, 400, 500, 600, 700, 800, 900, 1000,
    1337, 1500, 2000)

fun List<String>.toCleanListOrNull() =
    map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().takeIf { it.isNotEmpty() }

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?) {
    fun urlSlug() = url.takeLastWhile { it != '/' }
}

data class Stats(
    val overview: Map<String, Int>,
    val games_by: StatsGamesBy,
)

data class StatsGamesBy(
    val genre: Map<String, Int>,
    val game_modes: Map<String, Int>,
    val supported_input: Map<String, Int>,
    val languages: Map<String, Int>,
    val available_country: Map<String, Int>,
    val accessibility_features: Map<String, Int>,
)

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
}

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/")),
    scriptName = "Update for Stadia Games API",
) {
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
        val detail = (readJsonOrNull<GameDetail>("detail/${game.urlSlug()}.json")
            ?: Jsoup.parse(URL(game.url), 10000)
                .parseGameDetail(game)
                .also { gameDetail -> writeJson("detail/${game.urlSlug()}.json", gameDetail) })
        detail.copy(title = game.title, url = game.url, img = game.img, button = game.button)
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
        sendTelegramMessages(
            messages = telegramMessages(games = newGames, singular = "a new game", plural = "new games"),
            parse_mode = ParseMode.Markdown,
            disable_notification = isSilentNotification,
        )
        sendTweets(messages = twitterMessages(games = newGames, singular = "A new game", plural = "new games"))

        // ## New demos: assuming `it.button != null` means "has demo"
        // 1. Find new demos
        val oldGamesDemosMap = oldAllGames
            .filter { it.button != null }
            .associate { it.title to it.button!! }
        val newGamesDemo = allGameDetails.filter { it.button != null && oldGamesDemosMap[it.title] != it.button }

        // 2. Send notifications
        val newGamesFiltered = newGamesDemo.filter { it !in newGames }      // to reduce noise
        sendTelegramMessages(
            messages = telegramMessages(games = newGamesFiltered, singular = "a new demo", plural = "new demos"),
            parse_mode = ParseMode.Markdown,
            disable_notification = isSilentNotification,
        )
        sendTweets(messages = twitterMessages(games = newGamesFiltered, singular = "A new demo", plural = "new demos"))

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

        // # Update stats  (in `/data/` folder)
        val oldStats = readJsonOrNull<Stats>("stats.json")

        fun statsGamesBy(attrValues: (GameDetail) -> List<String>) = allGameDetails.flatMap(attrValues)
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareBy({ it.second }, { it.first }))
            .reversed()
            .toMap()

        val stats = Stats(
            overview = mapOf(
                "Stadia games" to allGameDetails.size,
                "Stadia demos" to allGameDetails.filter { it.button != null }.size,
                "Games on Stadia Pro" to allGameDetails.filter { it.isPro() }.size,
                "Games on Ubisoft+" to allGameDetails.filter { it.isUbisoftPlus() }.size,
            ),
            games_by = StatsGamesBy(
                genre = statsGamesBy { it.genre.orEmpty() },
                game_modes = statsGamesBy { it.game_modes.orEmpty() },
                supported_input = statsGamesBy { it.supported_input.orEmpty() },
                languages = statsGamesBy { it.languages.orEmpty() },
                available_country = statsGamesBy { it.available_country.orEmpty() },
                accessibility_features = statsGamesBy { it.accessibility_features.orEmpty() },
            ),
        )

        writeJson("stats.json", stats)
        writeText(
            pathname = "stats.md",
            text = buildString {
                fun appendTable(name: String, valuesMap: Map<String, Int>) {
                    appendLine("| $name | # of games | Milestone reached | Next milestone |")
                    appendLine("| --- | --- | --- | --- |")
                    valuesMap.forEach { (key, value) -> appendLine("| $key | $value | ${value.currentMilestone()}+ | ${value.nextMilestone()}+ |") }
                }

                appendLine("# Stadia games stats")
                appendLine("## Overview")
                appendTable("Stats", stats.overview)

                fun gamesBy(name: String, valuesMap: Map<String, Int>) {
                    appendLine()
                    appendLine("## Games by **$name**")
                    appendTable(name, valuesMap)
                }

                gamesBy(name = "Genre", valuesMap = stats.games_by.genre)
                gamesBy(name = "Game modes", valuesMap = stats.games_by.game_modes)
                gamesBy(name = "Supported input", valuesMap = stats.games_by.supported_input)
                gamesBy(name = "Languages", valuesMap = stats.games_by.languages)
                gamesBy(name = "Available country", valuesMap = stats.games_by.available_country)
                gamesBy(name = "Accessibility features", valuesMap = stats.games_by.accessibility_features)
            }
        )

        if (oldStats != null) {
            fun MessagesScope.milestonesFor(
                title: String,
                appendTitle: MessageAppendStrategy,
                onStats: (Stats) -> Map<String, Int>,
            ) {
                var found = false
                val old = onStats(oldStats)
                val new = onStats(stats)
                old.forEach { (key, value) ->
                    val currentValue = new.getValue(key)
                    val previousMilestone = value.nextMilestone()
                    if (currentValue >= previousMilestone) {
                        if (!found) {
                            found = true
                            addMessage(appendIf = appendTitle, joinGroups = "\n\n") {
                                text("New milestones for $title!")
                            }
                        }
                        addMessage {
                            text("$key: $currentValue games, reached ${currentValue.currentMilestone()}+!")
                        }
                    }
                }
            }

            sendTelegramMessages(
                messages = buildMessages {
                    milestonesFor("*Stadia Games*", AppendIfNotDivide) { it.overview - "Games on Stadia Pro" }
                    milestonesFor("Games by *Genre*", AppendIfNotDivide) { it.games_by.genre }
                    milestonesFor("Games by *Game modes*", AppendIfNotDivide) { it.games_by.game_modes }
                    milestonesFor("Games by *Supported input*", AppendIfNotDivide) { it.games_by.supported_input }
                    milestonesFor("Games by *Languages*", AppendIfNotDivide) { it.games_by.languages }
                    milestonesFor("Games by *Available country*", AppendIfNotDivide) { it.games_by.available_country }
                    milestonesFor("Games by *Accessibility features*", AppendIfNotDivide) { it.games_by.accessibility_features }
                },
                parse_mode = ParseMode.Markdown,
                disable_notification = isSilentNotification,
            )

            sendTweets(buildMessages {
                milestonesFor("#Stadia Games", AppendNever) { it.overview - "Games on Stadia Pro" }
                milestonesFor("#Stadia by Genre", AppendNever) { it.games_by.genre }
                milestonesFor("#Stadia by Game modes", AppendNever) { it.games_by.game_modes }
                milestonesFor("#Stadia by Supported input", AppendNever) { it.games_by.supported_input }
                milestonesFor("#Stadia by Languages", AppendNever) { it.games_by.languages }
                // milestonesFor("#Stadia by Available country", AppendNever) { it.games_by.available_country }
                // milestonesFor("#Stadia by Accessibility features", AppendNever) { it.games_by.accessibility_features }
            })
        }
    }
}

// Set up: Games converter
fun MessageL1Scope.gameAsMarkdown(game: GameDetail) {
    text("[${game.title}](${game.url})")
    if (game.button != null) text(" - ${game.button}")
    listOfNotNull(
        "#StadiaPro".takeIf { game.isPro() },
        "#UbisoftPlus".takeIf { game.isUbisoftPlus() },
    ).joinToString().also { services ->
        if (services.isNotEmpty()) text(" - available also with $services")
    }
    if (game.hasLocalCoop()) text(" #LocalCoop")
    if (game.hasLocalMultiplayer()) text(" #LocalMultiplayer")
    if (game.hasDirectTouch()) text(" #DirectTouch")
}

fun MessageL1Scope.gameAsTwitterText(
    game: GameDetail,
    withButton: Boolean = true,
    showDetails: Boolean = true,
    withUrl: Boolean = true
) {
    text(game.title)
    if (withButton && game.button != null) text(" - ${game.button}")
    listOfNotNull(
        "#StadiaPro".takeIf { game.isPro() },
        "#UbisoftPlus".takeIf { game.isUbisoftPlus() },
    ).joinToString().also { services ->
        if (showDetails && services.isNotEmpty()) text(" - available also with $services")
    }
    if (showDetails && game.hasLocalCoop()) text(" #LocalCoop")
    if (showDetails && game.hasLocalMultiplayer()) text(" #LocalMultiplayer")
    if (showDetails && game.hasDirectTouch()) text(" #DirectTouch")
    if (withUrl) {
        text(" ")
        twitterUrl(game.url)
    }
}

fun telegramMessages(
    games: List<GameDetail>,
    singular: String,
    plural: String,
): Messages = buildMessages {
    when (games.size) {
        0 -> {
            /* do nothing */
        }

        1 -> {
            addMessage {
                text("There is *$singular*: ")
                gameAsMarkdown(game = games.first())
            }
        }

        in 2..5 -> {
            addMessage {
                text("There are *${games.size} $plural*:\n")
            }
            games.forEachIndexed { index, game ->
                addMessage(appendIf = AppendNever) {
                    text("${index + 1}. ")
                    gameAsMarkdown(game = game)
                }
            }
        }

        else -> {
            addMessage {
                text("There are *${games.size} $plural*:\n")
            }
            games.forEachIndexed { index, game ->
                addMessage {
                    text("${index + 1}. ")
                    gameAsMarkdown(game = game)
                }
            }
            addMessage {
                text("\nThere are ${games.size} $plural on #Stadia - Full changelog: $changelogUrl")
            }
        }
    }
}

fun twitterMessages(
    games: List<GameDetail>,
    singular: String,
    plural: String,
): Messages = buildMessages {
    when (games.size) {
        0 -> {
            /* do nothing */
        }

        1 -> {
            addMessage {
                text("$singular on #Stadia\n")
                gameAsTwitterText(game = games.first())
            }
        }

        in 2..8 -> {
            addMessage {
                text("${games.size} $plural on #Stadia\n")
            }
            games.forEachIndexed { index, game ->
                addMessage(appendIf = if (index == 0) AppendIfNotDivide else AppendNever) {
                    text("${index + 1}. ")
                    gameAsTwitterText(game = game)
                }
            }
        }

        else -> {
            addMessage {
                text("${games.size} $plural on #Stadia\n")
            }
            games.forEachIndexed { index, game ->
                addMessage {
                    text("${index + 1}. ")
                    gameAsTwitterText(game = game)
                }
            }
            addMessage(appendIf = AppendNever) {
                text("There are ${games.size} $plural on #Stadia - Full changelog: ")
                twitterUrl(changelogUrl)
            }
        }
    }
}

fun List<GameDetail>.toMarkdownNumberedList(): String = buildMessages {
    forEach { game ->
        addMessage(appendIf = AppendIfNotDivide) {
            text("1. ")
            gameAsMarkdown(game)
        }
    }
}.toStrings(Int.MAX_VALUE).firstOrNull().orEmpty()

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

fun Int.currentMilestone() = milestones.lastOrNull { it <= this }.takeIf { it != milestones.last() }
    ?: ((this / 1000) * 1000).coerceAtLeast(1)

fun Int.nextMilestone() = milestones.firstOrNull { it > this }
    ?: ((this / 1000 + 1) * 1000)

exitProcess(status = 0)
