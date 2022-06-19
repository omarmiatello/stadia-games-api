#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.3")
@file:DependsOn("com.github.omarmiatello.telegram:client-jvm:6.0")
@file:DependsOn("io.ktor:ktor-client-okhttp-jvm:2.0.2")  // required for com.github.omarmiatello.telegram:client
// @file:Import("games_common.main.kts")   // works, but IntelliJ does not support it, yet!

import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.readJsonOrNull
import com.github.omarmiatello.telegram.TelegramClient

launchKotlinScriptToolbox(
    scriptName = "Telegram notification for Stadia Games",
    filepathPrefix = "data/",
) {
    data class Game(val title: String, val img: String, val button: String?)
    data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)

    val gamesResponse: GameListResponse = readJsonOrNull("games.json")!!
    val telegramClient = TelegramClient(apiKey = readSystemPropertyOrNull("TELEGRAM_BOT_APIKEY")!!)
    val defaultChatId = readSystemPropertyOrNull("TELEGRAM_CHAT_ID")!!

    suspend fun sendMessage(text: String, chatId: String = defaultChatId) {
        println("💬 $text")
        telegramClient.sendMessage(chat_id = chatId, text = text)
    }

    sendMessage("Ciao ${gamesResponse.games.size} giochi!")
}
