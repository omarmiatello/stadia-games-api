#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.0")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJson
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?)

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.fromDefaults(filepathPrefix = "data/")),
) {

    val telegramChatIds: List<String> = (readSystemProperty("TELEGRAM_CHAT_ID_LIST").split(",") +
            readSystemProperty("TELEGRAM_CHAT_ID")).distinct()

    val game = readJson<GameListResponse>("games.json").games.random()

    telegramChatIds.forEach { chatId ->
        sendTelegramMessage(
            text = buildString {
                append("[\u200B](${game.img})Have you tried [${game.title}](${game.url})?")
                if (game.button != null) append(" - ${game.button}")
            },
            chatId = chatId,
        )
    }
}

exitProcess(status = 0)