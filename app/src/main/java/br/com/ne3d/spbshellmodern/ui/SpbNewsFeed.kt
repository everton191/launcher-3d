package br.com.ne3d.spbshellmodern.ui

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

internal data class SpbNewsArticle(val title: String, val url: String)

/** RSS/Atom reader: bounded HTTPS requests, no document type declarations or external entities. */
internal object SpbNewsFeed {
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun fetch(url: String): List<SpbNewsArticle> {
        var target = normalizedHttpsUrl(url) ?: error("HTTPS required")
        repeat(5) {
            val connection = URI(target).toURL().openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                connection.setRequestProperty("User-Agent", "SPBShellModern/1.0")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Missing redirect")
                    target = normalizedHttpsUrl(URI(target).resolve(location).toString()) ?: error("Insecure redirect")
                } else {
                    check(status in 200..299) { "HTTP $status" }
                    check(connection.contentLengthLong <= MAX_BYTES) { "Feed too large" }
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            check(output.size() + read <= MAX_BYTES) { "Feed too large" }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    return parse(bytes, target)
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }

    internal fun parse(bytes: ByteArray, baseUrl: String): List<SpbNewsArticle> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(ByteArrayInputStream(bytes), null)
        val articles = mutableListOf<SpbNewsArticle>()
        var depth = -1
        var title = ""
        var link = ""
        var event = parser.eventType
        var tokens = 0
        while (event != XmlPullParser.END_DOCUMENT && articles.size < 20) {
            check(++tokens < 100_000) { "Feed too complex" }
            check(event != XmlPullParser.DOCDECL) { "Document types are unsupported" }
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "item", "entry" -> if (depth == -1) { depth = parser.depth; title = ""; link = "" }
                    "title" -> if (depth != -1 && parser.depth == depth + 1) title = parser.nextText().trim().take(400)
                    "link" -> if (depth != -1 && parser.depth == depth + 1) {
                        val href = parser.getAttributeValue(null, "href")
                        val relation = parser.getAttributeValue(null, "rel")
                        if (href != null) {
                            if (relation.isNullOrBlank() || relation == "alternate") link = href.trim()
                        } else link = parser.nextText().trim()
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.depth == depth && parser.name.lowercase() in listOf("item", "entry")) {
                val normalized = runCatching { normalizedHttpsUrl(URI(baseUrl).resolve(link).toString()) }.getOrNull()
                if (title.isNotBlank() && link.isNotBlank() && normalized != null) articles += SpbNewsArticle(title, normalized)
                depth = -1
            }
            event = parser.nextToken()
        }
        return articles.distinctBy { it.url }
    }

    fun encode(articles: List<SpbNewsArticle>): String = JSONArray().apply {
        articles.take(20).forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
    }.toString()

    fun decode(json: String): List<SpbNewsArticle> = runCatching {
        val array = JSONArray(json)
        (0 until minOf(array.length(), 20)).mapNotNull { i ->
            val item = array.getJSONObject(i)
            normalizedHttpsUrl(item.optString("url"))?.let { SpbNewsArticle(item.optString("title").take(400), it) }
        }
    }.getOrDefault(emptyList())
}
