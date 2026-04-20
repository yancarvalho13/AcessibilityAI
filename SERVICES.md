# Services Guide

Este documento descreve como usar os servicos e APIs do projeto, com foco em arquitetura limpa e consumo por `Flow`.

## Arquitetura

- `domain/*`: contratos e modelos (sem detalhes de Android, exceto tipos necessarios de camera).
- `data/*`: implementacoes Android dos contratos.
- `app/ServiceGraph`: ponto unico para obter APIs (`voiceServiceApi`, `overlayServiceApi`, `cameraServiceApi`, `sceneAnalysisApi`, `speechToTextApi`, `textToSpeechApi`, `appLauncherApi`).
- `presentation/*`: `ViewModel` consome somente interfaces de dominio.

## Gemini Scene Analysis API

Arquivos principais:

- `app/src/main/java/com/example/myapplication/domain/analysis/SceneAnalysisApi.kt`
- `app/src/main/java/com/example/myapplication/data/analysis/GeminiSceneAnalysisApi.kt`

### O que ele faz

- Recebe bytes de foto ou video.
- Envia para a API do Gemini com prompt minimo vindo de `.env`.
- Retorna texto com a analise do que esta acontecendo na foto ou no video.

### API

```kotlin
interface SceneAnalysisApi {
    suspend fun analyzePhoto(photoBytes: ByteArray, prompt: String? = null): String
    suspend fun analyzeVideo(videoBytes: ByteArray, prompt: String? = null): String
}
```

### Configuracao `.env`

Arquivo na raiz do projeto:

```dotenv
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-2.5-flash
GEMINI_API_VERSION=v1beta
GEMINI_PHOTO_PROMPT=O que esta acontecendo nesta foto?
GEMINI_VIDEO_PROMPT=O que esta acontecendo neste video?
```

Esses valores sao injetados no `BuildConfig` no build (`app/build.gradle.kts`).
Se um modelo/version falhar, a implementacao tenta fallback automatico entre `v1beta`/`v1` e modelo padrao `gemini-2.5-flash`.

## Voice Service

Arquivos principais:

- `app/src/main/java/com/example/myapplication/domain/voice/VoiceServiceApi.kt`
- `app/src/main/java/com/example/myapplication/data/voice/AndroidVoiceServiceApi.kt`
- `app/src/main/java/com/example/myapplication/VoiceCommandService.kt`
- `app/src/main/java/com/example/myapplication/voice/VoiceServiceContract.kt`

### O que ele faz

- Inicia captura de microfone em `ForegroundService`.
- Publica nivel de voz (0..100) em tempo real.
- Salva a ultima gravacao em arquivo WAV no armazenamento interno (`filesDir/voice_recordings`).
- Exponibiliza metadados da ultima gravacao em `state.lastRecording`.
- Exponibiliza bytes da ultima gravacao com `getLastRecordingBytes()`.

### API

```kotlin
interface VoiceServiceApi {
    val state: StateFlow<VoiceServiceState>
    fun startRecording()
    fun stopRecording()
    suspend fun getLastRecordingBytes(): ByteArray?
}
```

`VoiceServiceState`:

- `isRecording`: se esta gravando no momento.
- `level`: nivel instantaneo de volume.
- `lastRecording`: referencia da ultima gravacao (`filePath`, `durationMs`, `sizeBytes`, `sampleRate`, `channelCount`).

### Exemplo de uso

```kotlin
val voiceApi = (application as MyApplication).serviceGraph.voiceServiceApi

voiceApi.startRecording()
// ... aguarda alguns segundos ...
voiceApi.stopRecording()

val wavBytes = voiceApi.getLastRecordingBytes()
```

## Overlay Service

Arquivos principais:

- `app/src/main/java/com/example/myapplication/domain/overlay/OverlayServiceApi.kt`
- `app/src/main/java/com/example/myapplication/data/overlay/AndroidOverlayServiceApi.kt`
- `app/src/main/java/com/example/myapplication/OverlayService.kt`

### O que ele faz

- Valida permissao de sobreposicao (`SYSTEM_ALERT_WINDOW`).
- Abre tela de configuracao de permissao quando necessario.
- Exibe overlay temporario na tela.

### API

```kotlin
interface OverlayServiceApi {
    val state: StateFlow<OverlayServiceState>
    fun refreshPermissionState()
    fun openPermissionSettings()
    fun showOverlay()
}
```

## Accessibility Service

Arquivo principal:

- `app/src/main/java/com/example/myapplication/MyAccessibilityService.kt`

### O que ele faz

- E o ponto oficial de acessibilidade do Android para eventos de UI do sistema.
- Ja esta declarado no `AndroidManifest.xml` com config em `res/xml/accessibility_service_config.xml`.
- So funciona apos habilitacao manual do usuario nas configuracoes de acessibilidade.

## Camera Service API

Arquivos principais:

- `app/src/main/java/com/example/myapplication/domain/camera/CameraServiceApi.kt`
- `app/src/main/java/com/example/myapplication/data/camera/AndroidCameraServiceApi.kt`

### O que ele faz

- Abre camera e liga preview com CameraX.
- Captura foto e retorna `ByteArray`.
- Inicia gravacao de video.
- Para gravacao e retorna metadados do video gravado.
- Fecha camera e libera recursos.

### API

```kotlin
interface CameraServiceApi {
    val state: StateFlow<CameraServiceState>
    fun openCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView)
    suspend fun capturePhotoBytes(): ByteArray
    fun startVideoRecording()
    suspend fun stopVideoRecording(): CameraVideoRef?
    suspend fun getLastVideoBytes(): ByteArray?
    fun closeCamera()
}
```

`CameraServiceState`:

- `isCameraOpen`: camera ligada e com use cases bindados.
- `isRecordingVideo`: gravacao em andamento.
- `lastVideo`: referencia do ultimo video.

`CameraVideoRef`:

- `filePath`, `durationMs`, `sizeBytes`, `createdAtMs`.

### Exemplo de uso

```kotlin
val cameraApi = (application as MyApplication).serviceGraph.cameraServiceApi

cameraApi.openCamera(this, previewView)

val photoBytes: ByteArray = cameraApi.capturePhotoBytes()

cameraApi.startVideoRecording()
val videoRef = cameraApi.stopVideoRecording()
val videoBytes = cameraApi.getLastVideoBytes()

cameraApi.closeCamera()
```

## Speech APIs (STT/TTS)

Arquivos principais:

- `app/src/main/java/com/example/myapplication/domain/speech/SpeechToTextApi.kt`
- `app/src/main/java/com/example/myapplication/data/speech/AndroidSpeechToTextApi.kt`
- `app/src/main/java/com/example/myapplication/domain/speech/TextToSpeechApi.kt`
- `app/src/main/java/com/example/myapplication/data/speech/AndroidTextToSpeechApi.kt`

### O que fazem

- STT: usa `SpeechRecognizer` nativo em `pt-BR` para transformar voz em texto e preencher prompt.
- TTS: usa `TextToSpeech` nativo em `pt-BR` para ler a resposta da IA em voz alta.

### API STT

```kotlin
interface SpeechToTextApi {
    val state: StateFlow<SpeechToTextState>
    fun startListening()
    fun stopListening()
    fun cancelListening()
    fun release()
}
```

### API TTS

```kotlin
interface TextToSpeechApi {
    val state: StateFlow<TextToSpeechState>
    fun speak(text: String)
    fun stop()
    fun release()
}
```

## App Launcher API

Arquivos principais:

- `app/src/main/java/com/example/myapplication/domain/app/AppLauncherApi.kt`
- `app/src/main/java/com/example/myapplication/data/app/AndroidAppLauncherApi.kt`

### O que faz

- Recebe comando de voz convertido para texto e tenta identificar palavra-chave de app.
- Atualmente reconhece `whatsapp` (inclui variacoes como `whatsaap`, `zap`, `wpp`) e `youtube`.
- Abre o app correspondente via `PackageManager`.

### API

```kotlin
interface AppLauncherApi {
    fun openWhatsApp(): AppLaunchResult
    fun openYouTube(): AppLaunchResult
    fun openByVoiceCommand(command: String): AppLaunchResult
}
```

`AppLaunchResult` retorna o status (`Opened`, `AppNotInstalled`, `NoMatch`) e o app alvo quando aplicavel.

## Headless Voice Session (Step Engine)

Arquivos principais:

- `app/src/main/java/com/example/myapplication/service/HeadlessVoiceService.kt`
- `app/src/main/java/com/example/myapplication/service/HeadlessCommandExecutor.kt`
- `app/src/main/java/com/example/myapplication/domain/session/VoiceSessionEngine.kt`
- `app/src/main/java/com/example/myapplication/domain/session/steps/BootStep.kt`
- `app/src/main/java/com/example/myapplication/domain/session/steps/ListeningStep.kt`
- `app/src/main/java/com/example/myapplication/domain/session/steps/ExecuteCommandStep.kt`
- `app/src/main/java/com/example/myapplication/domain/session/steps/EndedStep.kt`

### Como funciona

- Ao abrir o app, a Activity inicia a sessao headless (se `RECORD_AUDIO` estiver concedida).
- O engine entra no `BootStep` e fala `"Estou escutando"`.
- Em seguida entra no `ListeningStep`, inicia STT e fica aguardando comando.
- Ao reconhecer comando, entra no `ExecuteCommandStep`, executa acao e volta para `ListeningStep`.
- Para comando `abrir camera`, o fluxo muda para steps especificos:
  - `CameraAwaitPhotoStep`: fala para dizer `tirar foto`.
  - `CapturePhotoStep`: captura a foto em modo headless.
  - `PhotoPromptStep`: escuta prompt livre e envia para analise Gemini da foto capturada.
- Cada ciclo de escuta reseta timeout de 30 segundos.
- Sem novos comandos por 30 segundos, o engine encerra automaticamente a sessao (`EndedStep`).

### Intents de voz atuais no headless

- Abrir WhatsApp
- Abrir YouTube
- Abrir camera -> tirar foto -> prompt por voz -> analise da foto
- Parar sessao
- Comandos de video continuam com feedback orientando uso da aba `Foto e Video`.

## Permissoes

Manifesto:

- `android.permission.RECORD_AUDIO`
- `android.permission.CAMERA`
- `android.permission.INTERNET`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`

Em runtime:

- Microfone e camera precisam ser solicitados em runtime na `Activity`.
- Overlay precisa ser concedido na tela de configuracao do Android.

## Seguranca da chave

- O arquivo `.env` esta no `.gitignore` e nao deve ser versionado.
- Para equipe, compartilhe somente um `.env` local ou um secret manager.
