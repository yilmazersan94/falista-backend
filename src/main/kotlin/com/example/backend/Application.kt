package com.example.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    install(ServerContentNegotiation) { json(json) }

    val apiKey = System.getenv("OPENAI_API_KEY") ?: ""
    val openAi = OpenAiClient(apiKey, json)

    routing {
        get("/") { call.respondText("ok") }
        post("/generate-fal") {
            val req = call.receive<GenerateFalRequest>()
            val result = openAi.generateFal(req)
            call.respond(result)
        }
    }
}

@Serializable
data class GenerateFalRequest(
    @SerialName("imageBase64") val imageBase64: String,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("userNote") val userNote: String? = null,
    @SerialName("date") val date: String? = null
)

@Serializable
data class GenerateFalResponse(
    val success: Boolean,
    val fortune: FortuneResponse? = null,
    val fortuneText: String? = null,
    val error: String? = null
)

class OpenAiClient(
    private val apiKey: String,
    private val json: Json
) {
    private val systemPrompt: String = """
Sen "Falista" adlŽñ bir mobil uygulama iÇõin ÇõalŽñYan bir fal motorusun.

GÇôrevin:
- KullanŽñcŽñnŽñ gÇônderdiŽYi FOTOŽ?RAFTAN yola ÇõŽñkarak,
- TÇ¬rkÇõe dilinde,
- Pozitif, gerÇõekÇõi, nazik ve sakin bir tonda,
- AnlamlŽñ, akŽñcŽñ ve duygulu bir gÇ¬nlÇ¬k fal metni ve ilgili alanlarŽñ Ç¬retmek.

Kurallar:
- Her zaman TÇoRKÇÅE yaz.
- Yapay zeka olduŽYunu belirtme.
- Ç-lÇ¬m, ciddi hastalŽñk, bÇ¬yÇ¬, lanet, muska, kara kehanet, felaket, korkutucu veya riskli temalara GŽøRME.
- Kesin tarihli/tam kesin gelecek tahminleri verme.
- Finansal, hukuki, tŽñbbi kararlar iÇõin net tavsiye verme.
- Her zaman sakin, gÇ¬ÇõlÇ¬, motive eden bir Ç¬slup kullan.
- FotoŽYraftaki ŽñYŽñk, renk, nesneler ve kompozisyondan semboller ÇõŽñkar ve fal metnine yedir.
- Fal metni HER ZAMAN uzun olsun: minimum 140 kelime, maksimum ~220 kelime.
- ÇÅIKTI SADECE JSON olsun.
- JSON YemasŽñna harfiyen uy, baYka hiÇõbir aÇõŽñklama ekleme.

JSON ?EMASI:
{
  "title": "2ƒ?"5 kelimelik kŽñsa baYlŽñk",
  "energy_score": 0-100,
  "mood_tag": "calm | hopeful | confident | romantic | reflective gibi tek kelime",
  "aura": {
    "color_name": "Rengin TÇ¬rkÇõe ismi",
    "color_hex": "#HEX",
    "meaning": "Bu aura renginin 1ƒ?"3 cÇ¬mlelik aÇõŽñklamasŽñ"
  },
  "symbols": [
    {
      "name": "Sembol adŽñ",
      "description": "FotoŽYrafta nasŽñl belirdiŽYi ve fal anlamŽñ"
    }
  ],
  "fortune_text": "3ƒ?"6 paragraf, 140ƒ?"220 kelime, sŽñcak ve akŽñcŽñ bir fal metni",
  "closing_message": "1ƒ?"2 cÇ¬mlelik kapanŽñY Çônerisi",
  "daily_message_short": "Tek cÇ¬mlelik gÇ¬n mesajŽñ",
  "lucky_emoji": "Emojilerden biri: §Y?? ƒoù §YOt §YO¯ §Y'®",
  "lucky_number": "1ƒ?"99 arasŽñ tam sayŽñ"
}
""".trimIndent()

    private val model = "gpt-4o-mini"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println("[OpenAI] $message")
                }
            }
            level = LogLevel.BODY
        }
    }

    suspend fun generateFal(req: GenerateFalRequest): GenerateFalResponse {
        if (apiKey.isBlank()) {
            return GenerateFalResponse(success = false, error = "OPENAI_API_KEY missing")
        }
        if (req.imageBase64.isBlank()) {
            return GenerateFalResponse(success = false, error = "imageBase64 is required")
        }

        val userContext = buildString {
            if (!req.userNote.isNullOrBlank()) append("KullanŽñcŽñ notu: ${req.userNote}. ")
            if (!req.date.isNullOrBlank()) append("Tarih: ${req.date}.")
        }.ifBlank { null }

        return runCatching {
            val contents = buildList {
                if (!userContext.isNullOrBlank()) add(ChatContent(type = "text", text = userContext))
                add(
                    ChatContent(
                        type = "image_url",
                        imageUrl = ChatImageUrl(url = "data:${req.mimeType};base64,${req.imageBase64}")
                    )
                )
            }
            val payload = ChatCompletionsRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = listOf(ChatContent(type = "text", text = systemPrompt))),
                    ChatMessage(role = "user", content = contents)
                ),
                maxTokens = 800
            )

            val response = client.post("https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                headers.append("Authorization", "Bearer $apiKey")
                setBody(payload)
            }
            if (!response.status.isSuccess()) {
                val raw = response.bodyAsText()
                throw IllegalStateException("OpenAI ${response.status.value}: $raw")
            }

            val bodyText = response.bodyAsText()
            val root = json.parseToJsonElement(bodyText).jsonObject
            val rawContent = extractContentText(root)
                ?: error("BoY OpenAI yanŽñtŽñ")
            val cleaned = rawContent
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = runCatching { json.decodeFromString<FortuneResponse>(cleaned) }.getOrNull()
            if (parsed != null) {
                GenerateFalResponse(success = true, fortune = parsed)
            } else {
                GenerateFalResponse(
                    success = true,
                    fortune = fallbackFortune(cleaned),
                    fortuneText = cleaned,
                    error = null
                )
            }
        }.getOrElse { ex ->
            GenerateFalResponse(success = false, error = ex.message ?: "Bilinmeyen hata")
        }
    }

    private fun extractContentText(root: JsonObject): String? {
        val contentElement: JsonElement? = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")

        fun primitiveContentOrNull(element: JsonElement?): String? {
            val primitive = element?.jsonPrimitive ?: return null
            return if (primitive.isString) primitive.content else null
        }

        return when (contentElement) {
            null -> null
            is JsonObject -> primitiveContentOrNull(contentElement["text"])
            is kotlinx.serialization.json.JsonArray -> {
                contentElement
                    .firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                    ?.jsonObject
                    ?.get("text")
                    ?.let { primitiveContentOrNull(it) }
            }
            else -> primitiveContentOrNull(contentElement)
        }?.takeIf { it.isNotBlank() }
    }

    private fun primitiveContentOrNull(element: JsonElement?): String? {
        val primitive = element?.jsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun fallbackFortune(fortuneText: String): FortuneResponse {
        val aura = Aura("Mystic", "#7256F0", "Sezgileri besleyen yumuYak bir aura.")
        val energy = kotlin.random.Random.nextInt(60, 96)
        return FortuneResponse(
            title = "Fal",
            energyScore = energy,
            moodTag = "calm",
            aura = aura,
            symbols = emptyList(),
            fortuneText = fortuneText,
            closingMessage = "Nazik adŽñmlarŽñn seni gÇ¬venli bir yola taYŽñyor.",
            dailyMessageShort = "BugÇ¬n sakin kal, ŽñYŽñŽYŽñn belirginleYiyor.",
            luckyEmoji = listOf("§Y??", "ƒoù", "§YOt", "§YO¯", "§Y'®").random(),
            luckyNumber = kotlin.random.Random.nextInt(1, 99)
        )
    }
}

@Serializable
data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ChatContent>
)

@Serializable
data class ChatContent(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ChatImageUrl? = null
)

@Serializable
data class ChatImageUrl(
    val url: String,
    val detail: String? = "auto"
)

@Serializable
data class ChatCompletionsResponse(
    val choices: List<ChatChoice>
)

@Serializable
data class ChatChoice(
    val message: ChatMessageOutput
)

@Serializable
data class ChatMessageOutput(
    val role: String,
    val content: List<ChatContentOutput>
)

@Serializable
data class ChatContentOutput(
    val type: String,
    val text: String? = null
)

@Serializable
data class FortuneResponse(
    val title: String,
    @SerialName("energy_score") val energyScore: Int,
    @SerialName("mood_tag") val moodTag: String,
    val aura: Aura,
    val symbols: List<FortuneSymbol>,
    @SerialName("fortune_text") val fortuneText: String,
    @SerialName("closing_message") val closingMessage: String,
    @SerialName("daily_message_short") val dailyMessageShort: String,
    @SerialName("lucky_emoji") val luckyEmoji: String,
    @SerialName("lucky_number") val luckyNumber: Int
)

@Serializable
data class Aura(
    @SerialName("color_name") val colorName: String,
    @SerialName("color_hex") val colorHex: String,
    val meaning: String
)

@Serializable
data class FortuneSymbol(
    val name: String,
    val description: String
)
