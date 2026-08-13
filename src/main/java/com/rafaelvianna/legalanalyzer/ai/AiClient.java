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

    /**
     * Variante que permite a um agente pedir um orçamento de tokens de saída
     * diferente do padrão global ({@code legal-analyzer.ai.max-tokens}).
     *
     * <p>Necessário para tarefas cuja resposta é grande por natureza — o caso
     * crítico é a consolidação, que precisa devolver o JSON unificado de
     * vários blocos parciais. Com o orçamento padrão (900 tokens ≈ 3 mil
     * caracteres) a resposta era cortada no meio e a análise falhava ao
     * interpretar o JSON, sempre no mesmo ponto da pipeline (55%).
     *
     * @param maxTokensSolicitado teto desejado de tokens gerados; {@code <= 0}
     *                            significa "usar o padrão configurado"
     */
    default String complete(String systemPrompt, String userPrompt, int maxTokensSolicitado) {
        return complete(systemPrompt, userPrompt);
    }
}
