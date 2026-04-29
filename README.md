# Voice Accessibility Lab

Projeto Android experimental voltado ao estudo de conceitos de acessibilidade, interacao por voz, analise multimodal e automacao assistiva. A intencao da pesquisa e entender como recursos nativos do Android e modelos generativos podem ser combinados para apoiar tecnologias assistivas para pessoas com deficiencia visual.

O aplicativo funciona como um laboratorio pratico: ele permite testar gravacao de voz, reconhecimento de fala, leitura por voz, captura de foto e video, analise de cena com Gemini, overlay e um fluxo headless controlado por comandos de voz.

## Objetivo da pesquisa

Este projeto investiga como transformar entradas do ambiente fisico e comandos naturais de voz em respostas acessiveis. O foco nao e entregar um produto final, mas validar conceitos que podem ser aplicados em tecnologias assistivas, como:

- descricao de imagens e videos para pessoas com deficiencia visual;
- interacao com o celular sem dependencia da tela;
- execucao de tarefas por comandos falados;
- feedback auditivo por Text-to-Speech;
- experimentacao com servicos de acessibilidade e overlays;
- construcao de fluxos conversacionais orientados por contexto.

## Funcionalidades

- Reconhecimento de fala em portugues brasileiro usando `SpeechRecognizer`.
- Respostas faladas usando `TextToSpeech` em `pt-BR`.
- Captura de fotos e videos com CameraX.
- Envio de foto e video para analise pelo Gemini.
- Prompt por texto ou voz para orientar a analise da cena.
- Sessao headless em foreground service, com escuta e resposta por voz.
- Comandos para abrir WhatsApp, YouTube e camera.
- Fluxos guiados para tirar foto, gravar video e analisar midia.
- Overlay simples para pesquisa de interacoes sobrepostas.
- Declaracao de `AccessibilityService` como base para futuras automacoes.
- Logs na interface para acompanhar a execucao dos fluxos.

## Tecnologias

- Kotlin
- Android SDK
- Jetpack Compose
- Material 3
- CameraX
- Android SpeechRecognizer
- Android TextToSpeech
- Foreground Service
- Accessibility Service
- OkHttp
- Gemini API
- JUnit

## Arquitetura

O projeto usa uma separacao simples por camadas:

- `app/src/main/java/com/example/myapplication/domain`: contratos, modelos e regras de negocio, incluindo a maquina de steps da sessao por voz.
- `app/src/main/java/com/example/myapplication/data`: implementacoes concretas de recursos Android e integracoes externas, como camera, voz, overlay, abertura de apps e Gemini.
- `app/src/main/java/com/example/myapplication/presentation`: `ViewModel` e estados observados pela UI.
- `app/src/main/java/com/example/myapplication/ui`: telas Compose.
- `app/src/main/java/com/example/myapplication/service`: orquestracao do modo headless, controle de camera sem tela e execucao de comandos.
- `app/src/main/java/com/example/myapplication/app/ServiceGraph.kt`: composicao manual das dependencias usadas pela aplicacao.

## Telas principais

O app tem duas abas principais em `MainActivity`.

### Voz

Tela para testar recursos de audio, overlay e sessao headless:

- iniciar e parar gravacao de voz;
- ler bytes do ultimo audio gravado;
- abrir configuracao de permissao de overlay;
- exibir overlay temporario;
- iniciar/parar sessao headless;
- cancelar o fluxo headless atual;
- visualizar logs da execucao.

### Foto e Video

Tela para testar captura e analise multimodal:

- abrir e fechar camera;
- capturar foto em bytes;
- iniciar e parar gravacao de video;
- ler bytes do ultimo video;
- informar prompt por texto ou fala;
- analisar foto ou video com Gemini;
- ler a resposta da analise em voz alta.

## Fluxo headless

O fluxo headless e executado por `HeadlessVoiceService`, um foreground service que escuta comandos, executa acoes e responde por voz. Ele usa `VoiceSessionEngine`, uma maquina de steps localizada em `domain/session`.

Fluxo geral:

1. `MainActivity` inicia `HeadlessVoiceService`.
2. `HeadlessVoiceService` chama `VoiceSessionEngine.start()`.
3. A engine produz acoes do tipo `VoiceSessionAction`.
4. O service executa acoes de STT, TTS, timeout e comandos.
5. `HeadlessCommandExecutor` executa comandos como abrir apps, controlar camera e chamar Gemini.
6. Os logs sao enviados por broadcast e exibidos na aba `Voz`.

## Exemplos de fluxos por voz

### Analise de foto

1. Usuario diz: `abrir camera`.
2. O app abre a camera no modo headless.
3. Usuario diz: `tirar foto`.
4. O app captura a foto e pergunta o que o usuario deseja saber.
5. Usuario diz um prompt livre, como `descreva o ambiente`.
6. A foto e enviada ao Gemini.
7. A resposta e lida por voz.

### Analise de video

1. Usuario diz: `abrir camera`.
2. Usuario diz: `iniciar gravacao`.
3. O app inicia a gravacao de video.
4. Usuario diz: `parar`.
5. O app salva o video e pergunta o que fazer com ele.
6. Usuario diz um prompt livre, como `resuma o que aconteceu`.
7. O video e enviado ao Gemini.
8. A resposta e lida por voz.

## Comandos reconhecidos

O parser de comandos usa normalizacao de texto e palavras-chave. Exemplos:

- `abrir WhatsApp`
- `abrir YouTube`
- `abrir camera`
- `tirar foto`
- `iniciar gravacao`
- `gravar video`
- `parar gravacao`
- `parar`
- `cancelar`
- `encerrar`

Durante os passos de analise de foto ou video, comandos livres tambem podem ser usados como prompt para o Gemini.

## Configuracao do Gemini

Crie um arquivo `.env` na raiz do projeto com base em `.env.example`:

```dotenv
GEMINI_API_KEY=coloque_sua_chave_aqui
GEMINI_MODEL=gemini-2.5-flash
GEMINI_API_VERSION=v1beta
GEMINI_PHOTO_PROMPT=O que esta acontecendo nesta foto?
GEMINI_VIDEO_PROMPT=O que esta acontecendo neste video?
```

Esses valores sao lidos em `app/build.gradle.kts` e expostos via `BuildConfig`.

## Permissoes

O app declara e usa permissoes sensiveis para viabilizar os experimentos:

- `RECORD_AUDIO`: reconhecimento de fala e gravacao de audio.
- `CAMERA`: captura de foto e video.
- `INTERNET`: chamadas para a API do Gemini.
- `SYSTEM_ALERT_WINDOW`: exibicao de overlay.
- `FOREGROUND_SERVICE`: execucao de servico em primeiro plano.
- `FOREGROUND_SERVICE_MICROPHONE`: uso de microfone no foreground service.
- `BIND_ACCESSIBILITY_SERVICE`: declaracao do servico de acessibilidade.

As permissoes de camera e microfone sao solicitadas em tempo de execucao. A permissao de overlay precisa ser concedida nas configuracoes do Android.

## Como executar

1. Abra o projeto no Android Studio.
2. Crie o arquivo `.env` na raiz do projeto.
3. Configure `GEMINI_API_KEY`.
4. Sincronize o Gradle.
5. Execute o app em um dispositivo ou emulador com suporte a microfone e camera.
6. Conceda as permissoes solicitadas.

No Windows, tambem e possivel compilar pela linha de comando:

```powershell
.\gradlew.bat assembleDebug
```

Em Linux/macOS:

```bash
./gradlew assembleDebug
```

## Testes

O projeto possui testes unitarios para a engine de sessao por voz e para o executor headless.

No Windows:

```powershell
.\gradlew.bat test
```

Em Linux/macOS:

```bash
./gradlew test
```

## Arquivos importantes

- `MainActivity.kt`: entrada principal, abas da UI e solicitacao de permissoes.
- `ServiceGraph.kt`: criacao das dependencias concretas.
- `VoiceSessionEngine.kt`: maquina de steps da sessao por voz.
- `VoiceCommandParser.kt`: reconhecimento de intencoes por palavras-chave.
- `HeadlessVoiceService.kt`: foreground service que orquestra STT, TTS e comandos.
- `HeadlessCommandExecutor.kt`: execucao de comandos headless.
- `AndroidCameraServiceApi.kt`: camera via CameraX para a UI.
- `HeadlessCameraController.kt`: camera usada pelo fluxo headless.
- `GeminiSceneAnalysisApi.kt`: integracao HTTP com Gemini.
- `AndroidSpeechToTextApi.kt`: reconhecimento de fala.
- `AndroidTextToSpeechApi.kt`: leitura de respostas por voz.
- `MediaAnalysisViewModel.kt`: estado e acoes da aba de foto e video.
- `VoiceLabScreen.kt`: tela da aba de voz.
- `MediaAnalysisScreen.kt`: tela da aba de foto e video.
- `SERVICES.md`: guia tecnico complementar dos servicos e fluxos.

## Estado atual e limitacoes

- O projeto e experimental e voltado a pesquisa.
- A interpretacao de comandos ainda e baseada em palavras-chave.
- O servico de acessibilidade esta declarado, mas a automacao de eventos ainda e minima.
- A analise de imagem e video depende de conexao com internet e chave Gemini valida.
- O comportamento de STT pode variar conforme dispositivo, idioma instalado e servicos do Android.
- O fluxo headless usa timeout de inatividade e pode ser cancelado por voz, pela notificacao ou pela UI.

## Possiveis evolucoes

- Melhorar a interpretacao de intencoes com NLP ou modelo local.
- Expandir comandos para navegacao assistida no sistema Android.
- Usar o `AccessibilityService` para leitura e interacao com conteudo de tela.
- Adicionar feedback haptico e sonoro alem de TTS.
- Criar perfis de uso para diferentes graus de deficiencia visual.
- Registrar metricas de tempo de resposta, taxa de erro e compreensao dos comandos.
- Validar os fluxos com usuarios reais e especialistas em acessibilidade.

## Observacao etica

Tecnologias assistivas devem ser desenvolvidas com participacao de pessoas com deficiencia visual, respeitando privacidade, autonomia, consentimento e seguranca. Como este projeto processa audio, imagem e video, qualquer uso fora de ambiente de pesquisa deve considerar protecao de dados e transparencia sobre o que esta sendo capturado e enviado para servicos externos.
