#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("com.github.omarmiatello.kotlin-script-toolbox:zero-setup:0.0.3")
@file:DependsOn("com.github.omarmiatello.telegram:client-jvm:6.0")
@file:DependsOn("io.ktor:ktor-client-okhttp-jvm:2.0.2")  // required for com.github.omarmiatello.telegram:client
@file:DependsOn("org.jsoup:jsoup:1.15.1")

import com.github.omarmiatello.kotlinscripttoolbox.core.launchKotlinScriptToolbox
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.gson
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.readJsonOrNull
import com.github.omarmiatello.kotlinscripttoolbox.zerosetup.writeJson
import com.github.omarmiatello.telegram.ParseMode
import com.github.omarmiatello.telegram.TelegramClient
import io.ktor.client.*
import io.ktor.client.request.*
import okio.ByteString
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.system.exitProcess

// Models (data classes)
data class TweetMessage(val text: String)
data class GameListResponse(val api_version: Int, val count: Int, val games: List<Game>)
data class Game(val title: String, val url: String, val img: String, val button: String?) {
    fun toMarkdown(withImage: Boolean = true) = buildString {
        if (withImage) append("[\u200B]($img)")
        append("[$title]($url)")
        if (button != null) append(" - $button")
    }

    fun toPlainTextForTwitter(withUrl: Boolean = true) = buildString {
        append(title)
        if (withUrl) append(" $url")
        if (button != null) append(" - $button")
    }
}

launchKotlinScriptToolbox(
    scriptName = "Update for Stadia Games API",
    filepathPrefix = "data/",
) {

    // Set up: Games converter
    fun List<Game>.toTelegramMessages(singular: String, plural: String, withImage: Boolean) = when (size) {
        0 -> emptyList()
        1 -> listOf("There is *$singular*: ${first().toMarkdown(withImage = withImage)}")
        in 2..5 -> listOf("There are *$size $plural*: ${first().toMarkdown(withImage = withImage)}") + drop(1).map { game -> game.toMarkdown() }
        else -> listOf("There are *$size $plural*: ${joinToString { it.toMarkdown(withImage = false) }}")
    }

    fun List<Game>.toTwitterMessages(singular: String, plural: String) = when (size) {
        0 -> emptyList()
        1 -> listOf("There is $singular: ${first().toPlainTextForTwitter()}")
        in 2..5 -> map { game -> "There are $size $plural, including: ${game.toPlainTextForTwitter()}" }
        else -> listOf("There are $size $plural: ${joinToString { it.toPlainTextForTwitter(withUrl = false) }}")
    }

    fun List<Game>.toMarkdownNumberedList() =
        joinToString("\n") { game -> "1. ${game.toMarkdown(withImage = false)}" }

    // Set up: Telegram notification
    val telegramClient = TelegramClient(apiKey = readSystemPropertyOrNull("TELEGRAM_BOT_APIKEY")!!)
    val defaultChatId = readSystemPropertyOrNull("TELEGRAM_CHAT_ID")!!
    suspend fun sendTelegramMessage(text: String, chatId: String = defaultChatId) {
        println("💬 ${text.take(4096)}")
        telegramClient.sendMessage(
            chat_id = chatId,
            text = text.take(4096),
            parse_mode = ParseMode.Markdown,
            disable_web_page_preview = false
        )
    }

    // Set up: Twitter notification
    val httpClient = HttpClient()
    val twitterConsumerKey = readSystemPropertyOrNull("TWITTER_CONSUMER_KEY")!!
    val twitterConsumerSecret = readSystemPropertyOrNull("TWITTER_CONSUMER_SECRET")!!
    val twitterAccessKey = readSystemPropertyOrNull("TWITTER_ACCESS_KEY")!!
    val twitterAccessSecret = readSystemPropertyOrNull("TWITTER_ACCESS_SECRET")!!
    suspend fun sendTweetMessage(text: String) {
        println("🐣 --> $text")
        val response: String = httpClient.post("https://api.twitter.com/2/tweets") {
            header("Content-Type", "application/json")

            val nonce: String = UUID.randomUUID().toString()
            val timestamp: Long = System.currentTimeMillis() / 1000L
            fun String.encodeUtf8() = URLEncoder.encode(this, "UTF-8").replace("+", "%2B")
            val urlEncoded = url.buildString().encodeUtf8()
            val paramEncoded = "oauth_consumer_key=$twitterConsumerKey&oauth_nonce=$nonce&oauth_signature_method=HMAC-SHA1&oauth_timestamp=$timestamp&oauth_token=$twitterAccessKey&oauth_version=1.0".encodeUtf8()
            val signature = Mac.getInstance("HmacSHA1")
                .apply { init(SecretKeySpec("$twitterConsumerSecret&$twitterAccessSecret".toByteArray(), "HmacSHA1")) }
                .doFinal("${method.value}&$urlEncoded&$paramEncoded".toByteArray())
                .let { ByteString.of(*it) }
                .base64()
                .encodeUtf8()

            header("Authorization", """OAuth oauth_consumer_key="$twitterConsumerKey", oauth_nonce="$nonce", oauth_signature="$signature", oauth_signature_method="HMAC-SHA1", oauth_timestamp="$timestamp", oauth_token="$twitterAccessKey", oauth_version="1.0"""")
            body = gson.toJson(TweetMessage(text))
        }
        println("🐥 <-- $response")
    }

    // # Parse games from https://stadia.google.com/games
    val gamesDoc = Jsoup.parse(URL("https://stadia.google.com/games"), 10000)
    val allGames = gamesDoc.select(".tLwy5")
        .map { gameDoc ->
            Game(
                title = gameDoc.select(".Oou9nd").first()!!.wholeText()
                    .trim()
                    .replace(" ", " "),
                url = "https://stadia.google.com/" + gameDoc.select(".nBACW.QAAyWd.wJYinb").first()!!
                    .attr("href"),
                img = gameDoc.select(".EvMdmf").first()!!.attr("src"),
                button = gameDoc.select(".Pyflbb").first()?.wholeText(),
            )
        }
        .distinctBy { it.title }
        .sortedBy { it.title }

    // # Find old games
    val oldAllGames = readJsonOrNull<GameListResponse>("games.json")?.games
    if (oldAllGames != null) {
        // ## New games: allGames - oldAllGames = newGames
        // 1. Find new games
        val newGames = oldAllGames
            .map { it.title }
            .toSet()
            .let { oldGamesTitles -> allGames.filter { it.title !in oldGamesTitles } }

        // 2. Send notifications
        newGames.toTelegramMessages(singular = "a new game", plural = "new games", withImage = true).forEach { sendTelegramMessage(it) }
        newGames.toTwitterMessages(singular = "a new game", plural = "new games").forEach { sendTweetMessage(it) }

        // ## New demos: assuming `it.button != null` means "has demo"
        // 1. Find new demos
        val newGamesDemo = oldAllGames
            .filter { it.button != null }
            .associate { it.title to it.button!! }
            .let { oldGamesDemosMap -> allGames.filter { it.button != null && oldGamesDemosMap[it.title] != it.button } }

        // 2. Send notifications
        newGamesDemo.toTelegramMessages(singular = "a new demo", plural = "new demos", withImage = true).forEach { sendTelegramMessage(it) }
        newGamesDemo.toTwitterMessages(singular = "a new demo", plural = "new demos").forEach { sendTweetMessage(it) }

        // ## Update changelog (in `/data/` folder)
        if (newGames.isNotEmpty() || newGamesDemo.isNotEmpty()) {
            val now = LocalDateTime.now()
            val changelogFilename = "changelog-${now.year}.md"
            writeText(
                pathname = changelogFilename,
                text = buildString {
                    appendLine("## ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}")
                    if (newGames.isNotEmpty()) {
                        appendLine()
                        append("### Found ")
                        appendLine(if (newGames.size == 1) "a new game" else "${newGames.size} new games")
                        appendLine(newGames.toMarkdownNumberedList())
                    }
                    if (newGamesDemo.isNotEmpty()) {
                        appendLine()
                        append("### Found ")
                        appendLine(if (newGamesDemo.size == 1) "a new demo" else "${newGamesDemo.size} new demos")
                        appendLine(newGamesDemo.toMarkdownNumberedList())
                    }
                    appendLine()

                    val oldHistory = readTextOrNull(pathname = changelogFilename)
                    if (oldHistory != null) append(oldHistory)
                }
            )
        }
    }

    // # Update API (in `/data/` folder)
    writeJson("games.json", GameListResponse(api_version = 1, count = allGames.size, games = allGames))
    writeText("games.md", allGames.toMarkdownNumberedList())
}

exitProcess(status = 0)
