#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.3")
@file:DependsOn("com.github.omarmiatello.telegram:client-jvm:6.0")
@file:DependsOn("io.ktor:ktor-client-okhttp-jvm:2.0.2")  // required for com.github.omarmiatello.telegram:client
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.readJsonOrNull
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.writeJson
import com.github.omarmiatello.telegram.ParseMode
import com.github.omarmiatello.telegram.TelegramClient
import org.jsoup.Jsoup
import java.net.URL
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?) {
    fun toMarkdown(withImage: Boolean = true) = buildString {
        if (withImage) append("[\u200B]($img)")
        append("[$title]($url)")
        if (button != null) append(" - $button")
    }
}

launchKotlinScriptToolbox(
    scriptName = "Update for Stadia Games API",
    filepathPrefix = "data/",
) {
    val localDebug = false

    // Set up: Games converter
    fun List<Game>.toMarkdownMessages(withImage: Boolean, singular: String, plural: String) = when (size) {
        0 -> emptyList()
        1 -> listOf("There is *$singular*: ${first().toMarkdown(withImage = withImage)}")
        in 2..5 -> listOf("There are *$size $plural*: ${first().toMarkdown(withImage = withImage)}") + drop(1).map { game -> game.toMarkdown() }
        else -> listOf("There are *$size $plural*: ${joinToString { it.toMarkdown(withImage = false) }}")
    }

    // Set up: Telegram notification
    val telegramClient = TelegramClient(apiKey = readSystemPropertyOrNull("TELEGRAM_BOT_APIKEY")!!)
    val defaultChatId = readSystemPropertyOrNull("TELEGRAM_CHAT_ID")!!
    suspend fun sendTelegramMessage(text: String, chatId: String = defaultChatId) {
        println("💬 ${text.take(4096)}")
        telegramClient.sendMessage(
            chat_id = chatId,
            text = text.take(4096),
            parse_mode = ParseMode.Markdown,
            disable_web_page_preview = false
        )
    }

    // # Parse games from https://stadia.google.com/games
    val gamesHtml = readTextOrNull("games.html")
    val gamesDoc = if (localDebug && gamesHtml != null) {
        Jsoup.parse(gamesHtml)
    } else {
        Jsoup.parse(URL("https://stadia.google.com/games"), 10000)
            .also { if (localDebug) writeText("games.html", it.toString()) }
    }
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

    // # Find old games
    val oldAllGames = readJsonOrNull<GameListResponse>("games.json")?.games
    if (oldAllGames != null) {
        // ## New games: allGames - oldAllGames = newGames
        // 1. Find new games
        val newGames = oldAllGames
            .map { it.title }
            .toSet()
            .let { oldGamesTitles -> allGames.filter { it.title !in oldGamesTitles } }

        // 2. Send notifications: Telegram
        newGames
            .toMarkdownMessages(withImage = true, singular = "a new game", plural = "new games")
            .forEach { message -> sendTelegramMessage(message) }

        // ## New demos: assuming `it.button != null` means "has demo"
        // 1. Find new demos
        val newGamesDemo = oldAllGames
            .filter { it.button != null }
            .associate { it.title to it.button!! }
            .let { oldGamesDemosMap -> allGames.filter { it.button != null && oldGamesDemosMap[it.title] != it.button } }

        // 2. Send notifications: Telegram
        newGamesDemo
            .toMarkdownMessages(withImage = true, singular = "a new demo", plural = "new demos")
            .forEach { message -> sendTelegramMessage(message) }
    }

    // # Update API (in `/data/` folder)
    writeJson("games.json", GameListResponse(api_version = 1, count = allGames.size, games = allGames))
    writeText("games.md", allGames.joinToString("\n") { "1. ${it.toMarkdown(withImage = false)}" })
}

exitProcess(status = 0)
