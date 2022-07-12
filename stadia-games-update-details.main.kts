#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.1.4")
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.BaseScope
import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.gson.*
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.ZeroSetupScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.exitProcess

val langRegex = "\\(.*?\\)".toRegex()
val pegiRegex = "(\\d+(?: \\(PROVISIONAL\\))?) ?(.*)".toRegex()
val dateParser = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun List<String>.toCleanListOrNull() =
    map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().takeIf { it.isNotEmpty() }

// Models (data classes)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(
    val title: String,
    val url: String,
    val img: String,
    val button: String?,
    val buttons: List<String>?,
    val description: String,
    val image_urls: List<String>?,
    val release_date: String?,
    val publisher: String?,
    val developer: String?,
    val languages: List<String>?,
    val available_country: List<String>?,
    val genre: List<String>?,
    val game_modes: List<String>?,
    val supported_input: List<String>?,
    val accessibility_features: List<String>?,
    val disclosure_details: List<String>?,
    val extra: Map<String, String>?,
    val notes: String?,
    val pegi_min_age: String?,
    val pegi_notes: List<String>?,
) {
    fun urlSlug() = url.takeLastWhile { it != '/' }
}

launchKotlinScriptToolbox(
    scope = ZeroSetupScope(baseScope = BaseScope.from(filepathPrefix = "data/")),
    scriptName = "Update for Stadia Games API",
) {
    val gameDetails = readJson<GameListResponse>("games.json").games
        .map { game ->
            Jsoup.parse(URL(game.url), 10000)
                .parseGameDetail(game)
                .also { gameDetail -> writeJson("detail/${game.urlSlug()}.json", gameDetail) }
        }

    println("DEBUG INFO:")
    println("buttons: " + gameDetails.flatMap { it.buttons.orEmpty() }.distinct().sorted())
    println("languages: " + gameDetails.flatMap { it.languages.orEmpty() }.distinct().sorted())
    println("available_country: " + gameDetails.flatMap { it.available_country.orEmpty() }.distinct().sorted())
    println("genre: " + gameDetails.flatMap { it.genre.orEmpty() }.distinct().sorted())
    println("game_modes: " + gameDetails.flatMap { it.game_modes.orEmpty() }.distinct().sorted())
    println("supported_input: " + gameDetails.flatMap { it.supported_input.orEmpty() }.distinct().sorted())
    println("accessibility_features: " + gameDetails.flatMap { it.accessibility_features.orEmpty() }.distinct().sorted())
    println("disclosure_details: " + gameDetails.flatMap { it.disclosure_details.orEmpty() }.distinct().sorted())
    println("pegi_age: " + gameDetails.mapNotNull { it.pegi_min_age }.distinct().sorted())
    println("pegi_notes: " + gameDetails.flatMap { it.pegi_notes.orEmpty() }.distinct().sorted())
}

fun Document.parseGameDetail(
    game: Game,
): Game {
    val data = select(".UyhU4c.n3017e")
        .mapNotNull {
            (it.select(".j35Pic").first()!!.text().lowercase() to it.ownText())
                .takeIf { it.second.isNotEmpty() }
        }
        .toMap().toMutableMap()
    println("open ${game.url}")
    val pegi = pegiRegex.find(select(".guztod").first()!!.text().trim().removePrefix("PEGI "))
        ?.groupValues.orEmpty()
    return Game(
        title = select(".uJClu").first()!!.wholeText()
            .trim()
            .replace(" ", " "),
        url = game.url,
        img = game.img,
        button = game.button,
        buttons = select(".qoHRNc .wJYinb").map { it.text().replace(" Circular loading icon", "") }
            .takeIf { it.isNotEmpty() },
        description = select(".WzDfPb").first()!!.text().trim(),
        image_urls = select(".BmoOQe").map { it.attr("src") }.takeIf { it.isNotEmpty() },
        //bundles = gamesDoc.select("#SeeMore .om9tXc").map { it.text() }.takeIf { it.isNotEmpty() },
        release_date = data.remove("release date")?.let { dateFormatter.format(dateParser.parse(it)) },
        publisher = data.remove("publisher"),
        developer = data.remove("developer"),
        languages = data.remove("languages")?.replace(langRegex, "")?.split(",")?.toCleanListOrNull(),
        available_country = data.remove("available in")?.split(",")?.toCleanListOrNull(),
        genre = data.remove("genre")?.split(",")?.toCleanListOrNull(),
        game_modes = data.remove("game modes")?.split(",")?.toCleanListOrNull(),
        supported_input = data.remove("supported input")?.split(",")?.toCleanListOrNull(),
        accessibility_features = data.remove("accessibility features")?.split(",")?.toCleanListOrNull(),
        disclosure_details = data.remove("disclosure details")?.split(",")?.toCleanListOrNull(),
        extra = data.takeIf { it.isNotEmpty() },
        notes = select(".Tykkac .MZ4wg").first()?.text()?.trim(),
        pegi_min_age = pegi.getOrNull(1),
        pegi_notes = pegi.getOrNull(2)?.split(",")?.toCleanListOrNull(),
    )
}

exitProcess(status = 0)
