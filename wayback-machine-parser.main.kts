#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.4")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.writeJson
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import org.jsoup.Jsoup
import java.io.File
import kotlin.system.exitProcess

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String?, val img: String, val button: String?)

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/wayback-machine/")),
) {
    val dir = File("/Users/omarmiatello/Desktop/waybackpack-0.4.0/build/lib/waybackpack/stadia2")
    dir.walkTopDown().filter { it.name == ".DS_Store" }.forEach { it.delete() }
    dir.walkTopDown().filter { it.isFile }.sorted().forEach { file ->
        val newFilename = file.parentFile.parentFile.name
        val gamesDoc = Jsoup.parse(file)
        val allGames = gamesDoc.select(".tLwy5")
            .map { gameDoc ->
                Game(
                    title = gameDoc.select(".Oou9nd").first()!!.wholeText()
                        .trim()
                        .replace(" ", " "),
                    url = gameDoc.select(".nBACW.QAAyWd.wJYinb").first()?.let { url ->
                        "https://stadia.google.com/" + url.attr("href")
                    },
                    img = gameDoc.select(".EvMdmf").first()!!.attr("src"),
                    button = gameDoc.select(".Pyflbb").first()?.wholeText(),
                )
            }
            .distinctBy { it.title }
            .sortedBy { it.title }

        if (allGames.isNotEmpty()) {
            println("Write $newFilename.json")
            writeJson("$newFilename.json", GameListResponse(api_version = 0, count = allGames.size, games = allGames))
        }
    }
}

exitProcess(status = 0)
