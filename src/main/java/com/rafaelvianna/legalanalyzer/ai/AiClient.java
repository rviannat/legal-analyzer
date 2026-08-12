package com.rafaelvianna.legalanalyzer.ai;

/**
 * Abstração de um cliente de LLM. Permite trocar o provedor de IA
 * (Anthropic, OpenAI, Azure OpenAI, um modelo local, etc.) sem alterar
 * os agentes que consomem esta interface.
 */
public interface AiClient {

    /**
     * Executa uma chamada de completion simples (sem streaming, sem
     * ferramentas), retornando o texto bruto gerado pelo modelo.
     *
     * @param systemPrompt instrução de sistema (papel/persona do agente)
     * @param userPrompt   conteúdo específico da tarefa
     * @return texto retornado pelo modelo (espera-se um JSON, ver AiJsonSupport)
     */
    String complete(String systemPrompt, String userPrompt);
}
