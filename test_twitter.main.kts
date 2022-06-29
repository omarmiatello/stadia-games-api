#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.8")

import com.github.omarmiatello.kotlinscripttoolbox.gson.readJson
import com.github.omarmiatello.kotlinscripttoolbox.twitter.sendTweet
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.launchKotlinScriptToolboxZeroSetup
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?)

launchKotlinScriptToolboxZeroSetup(filepathPrefix = "data/") {
    val game = readJson<GameListResponse>("games.json").games.random()
    sendTweet("Have you tried ${game.title}? ${game.url}")
}

exitProcess(status = 0)