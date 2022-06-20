#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.3")
@file:DependsOn("com.github.omarmiatello.telegram:client-jvm:6.0")
@file:DependsOn("io.ktor:ktor-client-okhttp-jvm:2.0.2")  // required for com.github.omarmiatello.telegram:client
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.gson
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.readJsonOrNull
import com.github.omarmiatello.telegram.TelegramClient
import org.jsoup.Jsoup
import java.net.URL
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val img: String, val button: String?)

launchKotlinScriptToolbox(
    scriptName = "Update for Stadia Games API",
    filepathPrefix = "data/",
) {
    val localDebug = false

    // Set up: Telegram notification
    val telegramClient = TelegramClient(apiKey = readSystemPropertyOrNull("TELEGRAM_BOT_APIKEY")!!)
    val defaultChatId = readSystemPropertyOrNull("TELEGRAM_CHAT_ID")!!
    suspend fun sendTelegramMessage(text: String, chatId: String = defaultChatId) {
        println("💬 $text")
        telegramClient.sendMessage(chat_id = chatId, text = text)
    }

    // Parse https://stadia.google.com/games
    val gamesHtml = readTextOrNull("games.html")
    val gamesDoc = if (localDebug && gamesHtml != null) {
        Jsoup.parse(gamesHtml)
    } else {
        Jsoup.parse(URL("https://stadia.google.com/games"), 10000)
            .also { if (localDebug) writeText("games.html", it.toString()) }
    }
    val games = gamesDoc.select(".tLwy5")
        .map { gameDoc ->
            Game(
                title = gameDoc.select(".Oou9nd").first()!!.wholeText()
                    .trim()
                    .replace(" ", " "),
                img = gameDoc.select(".EvMdmf").first()!!.attr("src"),
                button = gameDoc.select(".Pyflbb").first()?.wholeText(),
            )
        }
        .distinctBy { it.title }
        .sortedBy { it.title }

    // Prepare for comparison of GameListResponse
    val oldResponse = readJsonOrNull<GameListResponse>("games.json")
    val newResponse = GameListResponse(api_version = 1, count = games.size, games = games)

    // Update API: data/games.json
    writeText("games.json", gson.toJson(newResponse))

    // Send notification
    if (oldResponse != null) {
        val oldTitles = oldResponse.games.map { it.title }.toSet()
        val newGames = newResponse.games.filter { it.title !in oldTitles }
        if (newGames.isNotEmpty()) {
            val gamesString = newGames.joinToString { it.title }
            sendTelegramMessage(if (newGames.size == 1) {
                "There is a new game: $gamesString"
            } else {
                "There are ${newGames.size} new games: $gamesString"
            })
        }
    }
}

exitProcess(status = 0)