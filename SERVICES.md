# Services Guide

Este documento descreve como usar os servicos e APIs do projeto, com foco em arquitetura limpa e consumo por `Flow`.

## Arquitetura

- `domain/*`: contratos e modelos (sem detalhes de Android, exceto tipos necessarios de camera).
- `data/*`: implementacoes Android dos contratos.
- `app/ServiceGraph`: ponto unico para obter APIs (`voiceServiceApi`, `overlayServiceApi`, `cameraServiceApi`).
- `presentation/*`: `ViewModel` consome somente interfaces de dominio.

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

## Permissoes

Manifesto:

- `android.permission.RECORD_AUDIO`
- `android.permission.CAMERA`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`

Em runtime:

- Microfone e camera precisam ser solicitados em runtime na `Activity`.
- Overlay precisa ser concedido na tela de configuracao do Android.
