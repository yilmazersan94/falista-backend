package com.example.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    install(ServerContentNegotiation) { json(json) }

    val apiKey = System.getenv("OPENAI_API_KEY") ?: ""
    val openAi = OpenAiClient(apiKey, json)

    routing {
        post("/generate-fal") {
            val req = call.receive<GenerateFalRequest>()
            val result = openAi.generateFal(req)
            if (result.success) {
                call.respond(result)
            } else {
                call.respond(result.copy(success = false))
            }
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
    val fortuneText: String? = null,
    val error: String? = null
)

class OpenAiClient(
    private val apiKey: String,
    private val json: Json
) {
    private val systemPrompt = """
Sen "Falista" adlı bir mobil uygulama için çalışan fal motorusun. Fotoğraf ve kullanıcının notuna dayanarak pozitif, nazik, akıcı ve Türkçe bir fal yaz. Ölüm, hastalık, kara kehanet yok. 120-220 kelime arası uzun, hikâye gibi yaz.
""".trimIndent()

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println("[OpenAI] $message")
                }
            }
            level = LogLevel.INFO
        }
    }

    suspend fun generateFal(req: GenerateFalRequest): GenerateFalResponse {
        if (apiKey.isBlank()) {
            return GenerateFalResponse(success = false, error = "OPENAI_API_KEY missing")
        }
        return runCatching {
            val payload = OpenAiRequest(
                model = "gpt-5.1",
                input = listOf(
                    ContentBlock(type = "input_text", text = systemPrompt),
                    ContentBlock(
                        type = "input_image",
                        imageUrl = ImageUrl(
                            url = "data:${req.mimeType};base64,${req.imageBase64}",
                            detail = "high"
                        )
                    ),
                    ContentBlock(
                        type = "input_text",
                        text = "Kullanıcı notu: ${req.userNote ?: "not yok"}. Tarih: ${req.date ?: "belirtilmedi"}."
                    )
                ),
                maxOutputTokens = 800
            )

            val response = client.post("https://api.openai.com/v1/responses") {
                contentType(ContentType.Application.Json)
                headers.append("Authorization", "Bearer $apiKey")
                setBody(payload)
            }
            val body: OpenAiResponse = response.body()
            val fortune = body.output?.firstOrNull { it.type == "output_text" }?.text
                ?: body.output?.firstOrNull()?.text
                ?: "Fal üretilemedi."
            GenerateFalResponse(success = true, fortuneText = fortune)
        }.getOrElse { ex ->
            GenerateFalResponse(success = false, error = ex.message ?: "Bilinmeyen hata")
        }
    }
}

@Serializable
data class OpenAiRequest(
    val model: String,
    val input: List<ContentBlock>,
    @SerialName("max_output_tokens") val maxOutputTokens: Int
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String,
    val detail: String = "auto"
)

@Serializable
data class OpenAiResponse(
    val output: List<OutputBlock>? = null
)

@Serializable
data class OutputBlock(
    val type: String,
    val text: String? = null
)
