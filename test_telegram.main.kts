#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.5")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.buildMessages
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJson
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import com.github.omarmiatello.telegram.ParseMode
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?)

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/")),
) {
    val games = readJson<GameListResponse>("games.json").games.take(50)

    sendTelegramMessages(
        messages = buildMessages {
            games.forEachIndexed { index, game ->
                addMessage {
                    text(buildString {
                        if (index == 0) append("Have you tried?\n")
                        append("${index + 1}. [${game.title}](${game.url})")
                        if (game.button != null) append(" - ${game.button}")
                    })
                }
            }
        },
        parse_mode = ParseMode.Markdown,
    )
}

exitProcess(status = 0)