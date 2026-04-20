# Services Guide

Guia rapido para quem nunca viu o projeto.

## 1) Visao geral

O app tem duas trilhas principais:

- UI interativa (abas `Voz` e `Foto e Video`) via `MainActivity` + `ViewModel`.
- Sessao headless por steps (`HeadlessVoiceService`) que escuta comandos, executa acoes e fala respostas.

Arquitetura por camada:

- `domain/*`: contratos, modelos e regras de fluxo (step engine).
- `data/*`: implementacoes Android/infra (CameraX, STT, TTS, Gemini, launcher).
- `presentation/*`: `ViewModel` e estado de tela.
- `ui/*`: Compose screens.
- `service/*`: orquestracao headless + adaptacao para foreground service.
- `app/ServiceGraph.kt`: composicao de dependencias.

## 2) Quem chama quem

### UI normal

1. `MainActivity` cria `MainViewModel` e `MediaAnalysisViewModel`.
2. ViewModels usam interfaces do dominio (`VoiceServiceApi`, `CameraServiceApi`, `SceneAnalysisApi`, `SpeechToTextApi`, `TextToSpeechApi`).
3. Implementacoes concretas vem do `ServiceGraph` (`data/*`).

### Fluxo headless

1. `MainActivity` inicia `HeadlessVoiceService` com `ACTION_START_SESSION`.
2. `HeadlessVoiceService` chama `VoiceSessionEngine.start()`.
3. `VoiceSessionEngine` roda steps de `domain/session/steps/*` e produz `VoiceSessionAction`.
4. `HeadlessVoiceService` consome as actions:
   - STT/TTS (`SpeechToTextApi`, `TextToSpeechApi`)
   - comandos (`HeadlessCommandExecutor`)
5. `HeadlessCommandExecutor` usa:
   - `AppLauncherApi`
   - `HeadlessCameraApi`
   - `SceneAnalysisApi` (Gemini)
6. Logs headless sao enviados por broadcast (`ACTION_HEADLESS_LOG`) e exibidos na aba `Voz`.

## 3) Arquivos centrais

- Grafo e app:
  - `app/src/main/java/com/example/myapplication/app/MyApplication.kt`
  - `app/src/main/java/com/example/myapplication/app/ServiceGraph.kt`
- Headless:
  - `app/src/main/java/com/example/myapplication/service/HeadlessVoiceService.kt`
  - `app/src/main/java/com/example/myapplication/service/HeadlessCommandExecutor.kt`
  - `app/src/main/java/com/example/myapplication/service/HeadlessCameraApi.kt`
  - `app/src/main/java/com/example/myapplication/service/HeadlessCameraController.kt`
- Step engine:
  - `app/src/main/java/com/example/myapplication/domain/session/VoiceSessionEngine.kt`
  - `app/src/main/java/com/example/myapplication/domain/session/VoiceCommandParser.kt`
  - `app/src/main/java/com/example/myapplication/domain/session/steps/*.kt`
- STT/TTS:
  - `app/src/main/java/com/example/myapplication/data/speech/AndroidSpeechToTextApi.kt`
  - `app/src/main/java/com/example/myapplication/data/speech/AndroidTextToSpeechApi.kt`
- Camera UI:
  - `app/src/main/java/com/example/myapplication/data/camera/AndroidCameraServiceApi.kt`
  - `app/src/main/java/com/example/myapplication/presentation/MediaAnalysisViewModel.kt`

## 4) Fluxos de sessao (headless)

### Boot e escuta

- `BootStep` fala "Estou escutando" e entra em `ListeningStep`.
- `ListeningStep` inicia STT e reseta timeout.

### Camera -> foto -> prompt -> Gemini

1. Usuario: "abrir camera"
2. Step: `CameraAwaitPhotoStep` (abre camera headless e aguarda acao)
3. Usuario: "tirar foto"
4. Step: `CapturePhotoStep` (captura bytes)
5. Step: `PhotoPromptStep` (escuta prompt livre)
6. Executor: `AnalyzePhoto` -> Gemini -> TTS da resposta

### Camera -> gravacao -> parar -> prompt -> Gemini

1. Usuario: "abrir camera"
2. Usuario: "iniciar gravacao"
3. Step: `StartVideoStep` (start video)
4. Step: `VideoAwaitStopStep` (aguarda "parar")
5. Step: `StopVideoStep` (stop e salva bytes)
6. Step: `VideoPromptStep` (escuta prompt)
7. Executor: `AnalyzeVideo` -> Gemini -> TTS da resposta

## 5) Cancelamento e timeout

Cancelamento global (prioridade alta):

- Voz com palavras de cancelamento (quando STT estiver ativo).
- Acao da notificacao (`Cancelar fluxo`).
- Botao na aba `Voz` (`Cancelar fluxo atual`).

Comportamento no cancelamento:

- cancela execucao longa em andamento (ex.: Gemini),
- limpa pendencias/timeout,
- para STT/TTS,
- reinicia no `BootStep`.

Timeout:

- timeout e resetados pelos steps (`VoiceSessionAction.ResetTimeout`).
- durante `ExecuteCommand`, timeout ativo e limpo para nao encerrar sessao no meio de operacao longa.

## 6) Comandos reconhecidos (resumo)

- Apps: WhatsApp, YouTube.
- Camera: abrir camera, tirar foto, iniciar gravacao, parar gravacao.
- Sessao: parar/encerrar, cancelar fluxo.

Observacao: interpretacao final depende de contexto do step atual.

## 7) Configuracao Gemini (.env)

Arquivo na raiz (nao versionar):

```dotenv
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-2.5-flash
GEMINI_API_VERSION=v1beta
GEMINI_PHOTO_PROMPT=O que esta acontecendo nesta foto?
GEMINI_VIDEO_PROMPT=O que esta acontecendo neste video?
```

Valores vao para `BuildConfig` via `app/build.gradle.kts`.

## 8) Permissoes importantes

- `RECORD_AUDIO`
- `CAMERA`
- `INTERNET`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`

Runtime:

- microfone e camera sao solicitados na `MainActivity`.
- overlay e concedido via configuracao do Android.
