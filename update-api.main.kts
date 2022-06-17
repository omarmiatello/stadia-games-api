#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("org.jsoup:jsoup:1.15.1")
@file:DependsOn("com.google.code.gson:gson:2.9.0")

import com.google.gson.Gson
import org.jsoup.Jsoup
import java.io.File
import java.net.URL

println("🏁 Start")
val startTime = System.currentTimeMillis()

val json = Gson().newBuilder().setPrettyPrinting().create()

val gamesDoc = Jsoup.parse(URL("https://stadia.google.com/games"), 10000)

// -- useful for debug! --
//   File("games.html").writeText(gamesDoc.toString())
//   val gamesDoc = Jsoup.parse(File("games.html"))

data class Game(
    val title: String,
    val img: String,
    val button: String?,
)

data class GameListResponse(
    val api_version: Int,
    val count: Int,
    val games: List<Game>,
)

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

val response = GameListResponse(
    api_version = 1,
    count = games.size,
    games = games,
)

File("data/games.json").writeText(json.toJson(response))

println("🎉 Completed in ${System.currentTimeMillis() - startTime}ms - Stadia Games API updated!")
