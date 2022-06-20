#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.3")
@file:DependsOn("com.github.omarmiatello.telegram:client-jvm:6.0")
@file:DependsOn("io.ktor:ktor-client-okhttp-jvm:2.0.2")  // required for com.github.omarmiatello.telegram:client
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.readJsonOrNull
import com.github.omarmiatello.telegram.ParseMode
import com.github.omarmiatello.telegram.TelegramClient
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?) {
    fun toMarkdown(withImage: Boolean = true) = buildString {
        if (withImage) append("[\u200B]($img)")
        append("[$title]($url)")
        if (button != null) append(" ($button)")
    }
}

launchKotlinScriptToolbox(
    scriptName = "Test notifications",
    filepathPrefix = "data/",
) {
    // Set up: Telegram notification
    val telegramClient = TelegramClient(apiKey = readSystemPropertyOrNull("TELEGRAM_BOT_APIKEY")!!)
    val defaultChatId = readSystemPropertyOrNull("TELEGRAM_CHAT_ID")!!
    suspend fun sendTelegramMessage(text: String, chatId: String = defaultChatId) {
        println("💬 $text")
        telegramClient.sendMessage(
            chat_id = chatId,
            text = text,
            parse_mode = ParseMode.Markdown,
            disable_web_page_preview = false
        )
    }

    // Prepare for comparison of GameListResponse
    val oldResponse = readJsonOrNull<GameListResponse>("games.json") ?: return@launchKotlinScriptToolbox

    // Send notification
    oldResponse.games.take(1).forEach { game ->
        sendTelegramMessage(game.toMarkdown())
    }
}

exitProcess(status = 0)