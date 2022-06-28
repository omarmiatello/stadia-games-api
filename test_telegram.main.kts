#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.7")

import com.github.omarmiatello.kotlinscripttoolbox.telegram.sendTelegramMessage
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.launchKotlinScriptToolboxZeroSetup
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.readJson
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?)

launchKotlinScriptToolboxZeroSetup(filepathPrefix = "data/") {
    val game = readJson<GameListResponse>("games.json").games.random()
    sendTelegramMessage(buildString {
        append("[\u200B](${game.img})Have you tried [${game.title}](${game.url})?")
        if (game.button != null) append(" - ${game.button}")
    })
}

exitProcess(status = 0)