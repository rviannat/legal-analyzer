@echo off
REM ============================================================
REM  Inicializa o Ollama no modo mais leve possivel,
REM  pensado para maquinas com pouca RAM (ex.: 7GB, sem GPU).
REM ============================================================

REM Ja esta rodando? Nao inicia de novo.
tasklist /FI "IMAGENAME eq ollama.exe" 2>NUL | find /I "ollama.exe" >NUL
if "%ERRORLEVEL%"=="0" (
    echo O Ollama ja esta em execucao. Nada a fazer.
    goto :fim
)

REM --- Configuracoes de baixo consumo ---

REM So processa 1 requisicao por vez. Evita que o Ollama tente
REM paralelizar chamadas e multiplique o uso de memoria/CPU.
set OLLAMA_NUM_PARALLEL=1

REM Mantem apenas 1 modelo carregado na memoria por vez.
REM Se voce usar o modelo de chat (llama3.2:3b) e o de embeddings
REM (nomic-embed-text) na mesma sessao, o Ollama descarrega um
REM para carregar o outro, em vez de manter os dois na RAM juntos.
set OLLAMA_MAX_LOADED_MODELS=1

REM Cache da conversa (KV cache) comprimido em 8 bits em vez de
REM 16 bits. Isso e o que mais ajuda com pouca RAM: o KV cache
REM cresce junto com o num_ctx configurado no backend (4096), e
REM comprimi-lo corta boa parte desse consumo com perda minima
REM de qualidade.
set OLLAMA_KV_CACHE_TYPE=q8_0

REM Descarrega o modelo da RAM 5 minutos apos a ultima chamada,
REM em vez de manter carregado por 30 min (padrao) ocupando
REM memoria sem necessidade entre uma analise e outra.
set OLLAMA_KEEP_ALIVE=5m

REM --- Inicializacao ---
REM "start /min" abre em uma janela minimizada, entao ela nao
REM atrapalha, mas ainda existe (facil de achar na barra de
REM tarefas se precisar ver o log ou fechar).
echo Iniciando Ollama em modo leve (1 modelo por vez, cache comprimido)...
start "Ollama" /min ollama serve

echo.
echo Ollama iniciado. Aguarde alguns segundos antes de usar o app.
echo Para conferir se subiu certo, rode em outro terminal:
echo   curl http://127.0.0.1:11434
echo.

:fim
pause