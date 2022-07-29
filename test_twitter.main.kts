#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.5")

import com.github.omarmiatello.kotlinscripttoolbox.core.AppendNever
import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.buildMessages
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.readJson
import com.github.omarmiatello.kotlinscripttoolbox.twitter.twitterUrl
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?)

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/")),
) {
    val games = readJson<GameListResponse>("games.json").games.shuffled().take(3)
    sendTweets(buildMessages {
        games.forEach { game ->
            addMessage(appendIf = AppendNever) {
                text("Have you tried ${game.title}? ")
                twitterUrl(game.url)
            }
        }
    })
}

exitProcess(status = 0)