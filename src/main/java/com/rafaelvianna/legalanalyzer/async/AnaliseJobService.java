package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.LegalAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudService;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.persistence.ProcessoEntity;
import com.rafaelvianna.legalanalyzer.persistence.ProcessoPersistenceService;
import com.rafaelvianna.legalanalyzer.pdf.PaginaExtraida;
import com.rafaelvianna.legalanalyzer.pdf.PdfTextExtractionService;
import com.rafaelvianna.legalanalyzer.pdf.RelatorioPdfService;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class AnaliseJobService {
    private static final Logger log = LoggerFactory.getLogger(AnaliseJobService.class);
    private final PdfTextExtractionService pdfTextExtractionService;
    private final LegalAnalysisOrchestrator orchestrator;
    private final ProcessoIndexService indexService;
    private final DataJudService dataJudService;
    private final ProcessoPersistenceService persistenceService;
    private final RelatorioPdfService relatorioPdfService;
    private final Executor executor;
    private final Map<String, AnaliseJob> jobs = new ConcurrentHashMap<>();

    public AnaliseJobService(PdfTextExtractionService pdfTextExtractionService, LegalAnalysisOrchestrator orchestrator, ProcessoIndexService indexService, DataJudService dataJudService, ProcessoPersistenceService persistenceService, RelatorioPdfService relatorioPdfService, Executor legalAnalysisExecutor) {
        this.pdfTextExtractionService=pdfTextExtractionService; this.orchestrator=orchestrator; this.indexService=indexService; this.dataJudService=dataJudService;
        this.persistenceService=persistenceService; this.relatorioPdfService=relatorioPdfService; this.executor=legalAnalysisExecutor;
    }

    public AnaliseJobResponse iniciar(MultipartFile arquivo) {
        String nome=arquivo.getOriginalFilename()==null?"processo.pdf":arquivo.getOriginalFilename();
        final byte[] conteudo;
        try { conteudo=arquivo.getBytes(); } catch(Exception e){ throw new PdfProcessingException("Não foi possível preparar o PDF para processamento: "+e.getMessage(),e); }
        String id=UUID.randomUUID().toString(); AnaliseJob job=new AnaliseJob(id,nome); jobs.put(id,job);
        persistenceService.criar(id,nome,conteudo);
        log.info("[ANALISE:{}] UPLOAD RECEBIDO | arquivo={} | bytes={} | persistido=true | iniciando extração para localizar CNJ",id,nome,conteudo.length);
        executor.execute(()->processar(job,conteudo)); return AnaliseJobResponse.status(job);
    }

    public AnaliseJobResponse consultar(String id){ return AnaliseJobResponse.status(buscar(id)); }
    public AnaliseJob buscar(String id){ AnaliseJob job=jobs.get(id); if(job==null) throw new PdfProcessingException("Análise não encontrada: "+id); return job; }

    private void atualizar(AnaliseJob job, AnaliseStatus status, int progresso, String etapa, String mensagem){
        job.atualizar(status,progresso,etapa,mensagem);
        try { persistenceService.atualizar(job.id(),job.numeroProcesso(),ProcessoEntity.Status.valueOf(status.name()),progresso,etapa,mensagem); }
        catch(Exception e){ log.error("[ANALISE:{}] PERSISTENCIA | falha ao salvar status={} | {}",job.id(),status,e.getMessage(),e); }
    }

    private void indexarComSeguranca(AnaliseJob job, AnaliseProcessoResponse resultado){ try{ indexService.indexar(job.id(),job.paginas(),resultado,null); }catch(Exception e){ log.warn("[ANALISE:{}] RAG | falha ao indexar: {}",job.id(),e.getMessage()); } }

    private void consultarDataJudEmParalelo(AnaliseJob job){
        job.dataJud(DataJudInfo.aguardando(job.numeroProcesso()));
        log.info("[ANALISE:{}] CNJ ENCONTRADO | {} | iniciando busca na API pública DataJud/CNJ",job.id(),job.numeroProcesso());
        executor.execute(()->{ try{
            job.dataJud(new DataJudInfo(com.rafaelvianna.legalanalyzer.datajud.DataJudStatus.CONSULTANDO,job.numeroProcesso(),null,null,false,null,null,null,null,null,"Consultando a base pública do DataJud/CNJ.",Instant.now(),List.of()));
            log.info("[ANALISE:{}] DATAJUD | consultando processo CNJ={}",job.id(),job.numeroProcesso());
            DataJudInfo resultado=dataJudService.consultar(job.numeroProcesso()); job.dataJud(resultado);
            log.info("[ANALISE:{}] DATAJUD | status={} | encontrado={} | tribunal={} | classe={} | orgao={} | movimentos={}",job.id(),resultado.status(),resultado.encontrado(),resultado.tribunal(),resultado.classeProcessual(),resultado.orgaoJulgador(),resultado.quantidadeMovimentos());
        }catch(Exception e){ log.warn("[ANALISE:{}] DATAJUD | erro CNJ={}: {}",job.id(),job.numeroProcesso(),e.getMessage()); } });
    }

    private void processar(AnaliseJob job, byte[] conteudo){
        try{
            atualizar(job,AnaliseStatus.EXTRAINDO_PDF,10,"Extraindo PDF","Lendo e normalizando o conteúdo do documento.");
            log.info("[ANALISE:{}] PDF | extração iniciada | arquivo={}",job.id(),job.nomeArquivo());
            List<PaginaExtraida> paginas=pdfTextExtractionService.extractPages(conteudo,job.nomeArquivo());
            String texto=pdfTextExtractionService.extractText(conteudo,job.nomeArquivo());
            job.paginas(paginas); job.textoExtraido(texto); job.numeroProcesso(ProcessoIndexService.numeroProcesso(paginas,texto));
            log.info("[ANALISE:{}] PDF | páginas={} | caracteres={} | CNJ identificado={}",job.id(),paginas.size(),texto==null?0:texto.length(),job.numeroProcesso());
            if(job.numeroProcesso()!=null&&!job.numeroProcesso().isBlank()&&!"não identificado".equalsIgnoreCase(job.numeroProcesso())) consultarDataJudEmParalelo(job); else { job.dataJud(DataJudInfo.numeroNaoIdentificado()); log.info("[ANALISE:{}] DATAJUD | CNJ não identificado; consulta individual não será executada",job.id()); }
            atualizar(job,AnaliseStatus.ANALISANDO_PARTES,35,"Analisando partes e fatos","Identificando partes, cronologia, pedidos, decisões, prazos e documentos.");
            log.info("[ANALISE:{}] AGENTE BASE | iniciando análise jurídica do conteúdo",job.id());
            var resultado=orchestrator.analisar(job.nomeArquivo(),texto,(status,progresso,etapa,mensagem)->{ log.info("[ANALISE:{}] ETAPA | {} | {}% | {}",job.id(),etapa,progresso,mensagem); atualizar(job,status,progresso,etapa,mensagem); });
            atualizar(job,AnaliseStatus.GERANDO_RELATORIO,92,"Gerando relatório PDF","Montando o relatório final completo para exportação.");
            log.info("[ANALISE:{}] PDF FINAL | geração iniciada | arquivoBase={}",job.id(),job.nomeArquivo());
            byte[] relatorio=relatorioPdfService.gerar(job.nomeArquivo(),job.numeroProcesso(),resultado);
            String relatorioNome=nomeRelatorio(job.nomeArquivo());
            log.info("[ANALISE:{}] PDF FINAL | gerado | nome={} | bytes={}",job.id(),relatorioNome,relatorio.length);
            log.info("[ANALISE:{}] PERSISTENCIA | salvando PDF final no PostgreSQL",job.id());
            persistenceService.salvarRelatorio(job.id(),relatorio);
            log.info("[ANALISE:{}] PERSISTENCIA | PDF final confirmado no PostgreSQL | nome={} | bytes={}",job.id(),relatorioNome,relatorio.length);
            atualizar(job,AnaliseStatus.CONSOLIDANDO,95,"Indexando o caso","Montando o índice do processo para o briefing e o chat.");
            log.info("[ANALISE:{}] RAG | indexando resultado",job.id()); indexarComSeguranca(job,resultado);
            job.concluir(resultado);
            try { persistenceService.atualizar(job.id(),job.numeroProcesso(),ProcessoEntity.Status.CONCLUIDO,100,"Relatório pronto","Análise concluída com sucesso e PDF persistido no PostgreSQL."); }
            catch(Exception e){ log.error("[ANALISE:{}] PERSISTENCIA | falha ao marcar CONCLUÍDO: {}",job.id(),e.getMessage(),e); }
            log.info("[ANALISE:{}] CONCLUÍDO | CNJ={} | relatório={} | PDF persistido=true",job.id(),job.numeroProcesso(),relatorioNome);
        }catch(Exception e){
            log.error("[ANALISE:{}] ERRO | arquivo={} | etapa={} | progresso={} | {}",job.id(),job.nomeArquivo(),job.etapa(),job.progresso(),e.getMessage(),e);
            job.falhar(e.getMessage()==null?"Erro inesperado durante a análise.":e.getMessage());
            try { persistenceService.atualizar(job.id(),job.numeroProcesso(),ProcessoEntity.Status.ERRO,job.progresso(),job.etapa(),job.mensagem()); } catch(Exception persistencia){ log.error("[ANALISE:{}] PERSISTENCIA | falha ao salvar erro: {}",job.id(),persistencia.getMessage(),persistencia); }
        }
    }

    private String nomeRelatorio(String nome){ String base=nome==null?"processo":nome.replaceAll("(?i)\\.pdf$",""); return base+"-relatorio.pdf"; }
}
