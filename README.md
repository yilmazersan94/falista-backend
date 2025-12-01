# Falista Backend (Ktor)

Tek endpoint: `POST /generate-fal`

Gövde örneği:
```json
{
  "imageBase64": "...",
  "mimeType": "image/jpeg",
  "userNote": "kısa not",
  "date": "2025-11-30"
}
```

Yanıtlar:
```json
{ "success": true, "fortuneText": "..." }
```
veya
```json
{ "success": false, "error": "..." }
```

## Çalıştırma (lokal)
```bash
cd backend
./gradlew shadowJar
java -jar build/libs/backend-all.jar
```
Varsayılan port `PORT` env’den, yoksa 8080; host 0.0.0.0.

## Ortam değişkeni
- `OPENAI_API_KEY` (zorunlu)
- `PORT` (Render veriyor)

## Render kurulum kılavuzu
1. Render → New → Web Service → repo seç → root directory `backend`.
2. Build command: `./gradlew shadowJar`
3. Start command: `java -jar build/libs/backend-all.jar`
4. Environment vars: `OPENAI_API_KEY` (senin keyin), opsiyonel `LOG_LEVEL=info`. `PORT` Render sağlıyor.
5. Deploy → URL örn: `https://falista-backend.onrender.com`

## Mobil ayarı
`composeApp/src/commonMain/kotlin/com/example/falista/BackendConfig.kt`:
- `baseUrl = "https://falista-backend.onrender.com"`
- `useFakeBackend = false`

Anahtar sadece backend env’de; mobil tarafa inmez.
