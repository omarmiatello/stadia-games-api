#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.1")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.gson
import org.jsoup.Jsoup
import java.net.URL

launchKotlinScriptToolbox(
    scriptName = "Update for Stadia Games API",
    filepathPrefix = "data/",
) {
    val localDebug = false
    data class Game(val title: String, val img: String, val button: String?)
    data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)

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

    writeText("games.json", gson.toJson(
        GameListResponse(
            api_version = 1,
            count = games.size,
            games = games,
        )
    ))
}
