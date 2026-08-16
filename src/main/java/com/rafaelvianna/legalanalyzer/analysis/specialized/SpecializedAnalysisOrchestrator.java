package com.rafaelvianna.legalanalyzer.analysis.specialized;

import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.ContractAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DeadlineAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DocumentAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DraftingAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.EvidenceAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.LegalResearchAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.ProcessAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.SeniorLawyerAgent;
import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaStatus;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AgendaPrazosDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseContratualDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseProcessualDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ClassificacaoDocumentalDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.MatrizEvidenciasDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ParecerSeniorDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.PesquisaJuridicaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.RascunhoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Orquestra os oito agentes em cadeia, alimentando cada etapa com os relatórios já produzidos. */
@Service
public class SpecializedAnalysisOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(SpecializedAnalysisOrchestrator.class);
    private static final int TOTAL_AGENTES = 8;
    private static final int MAX_CONTEXTO_ANTERIOR = 20_000;
    private final DocumentAgent documentAgent; private final ProcessAgent processAgent; private final ContractAgent contractAgent;
    private final DeadlineAgent deadlineAgent; private final EvidenceAgent evidenceAgent; private final LegalResearchAgent legalResearchAgent;
    private final DraftingAgent draftingAgent; private final SeniorLawyerAgent seniorLawyerAgent; private final AppProperties properties;

    public SpecializedAnalysisOrchestrator(DocumentAgent documentAgent, ProcessAgent processAgent, ContractAgent contractAgent,
                                           DeadlineAgent deadlineAgent, EvidenceAgent evidenceAgent, LegalResearchAgent legalResearchAgent,
                                           DraftingAgent draftingAgent, SeniorLawyerAgent seniorLawyerAgent, AppProperties properties) {
        this.documentAgent=documentAgent; this.processAgent=processAgent; this.contractAgent=contractAgent; this.deadlineAgent=deadlineAgent;
        this.evidenceAgent=evidenceAgent; this.legalResearchAgent=legalResearchAgent; this.draftingAgent=draftingAgent;
        this.seniorLawyerAgent=seniorLawyerAgent; this.properties=properties;
    }
    public boolean pesquisaJuridicaHabilitada(){ return legalResearchAgent.disponivel(); }

    public AnaliseEspecializadaResponse analisar(String analiseBaseId,String numeroProcesso,String nomeArquivo,String textoExtraido,AnaliseProcessoResponse analiseBase,
                                                  AnaliseEspecializadaRequest request,SpecializedProgressListener listener){
        AnaliseEspecializadaRequest opcoes=request==null?AnaliseEspecializadaRequest.padrao():request;
        List<String> agentesExecutados=new ArrayList<>(), avisos=new ArrayList<>();
        String amostraTexto=amostra(textoExtraido), parteRepresentada=opcoes.parteRepresentadaOuNaoInformada(), contexto=opcoes.contextoOuVazio();

        listener.updateRich(AnaliseEspecializadaStatus.CLASSIFICANDO_DOCUMENTOS,10,"Document Agent",1,TOTAL_AGENTES,"CLASSIFYING_DOCUMENTS",
                "Identificando a natureza do material e classificando as peças.",List.of("Análise Base"),"Classificação documental será usada para roteamento.");
        ClassificacaoDocumentalDTO classificacao=executar("Document Agent",agentesExecutados,avisos,
                ()->documentAgent.classificar(nomeArquivo,analiseBase.documentosImportantes(),amostraTexto),
                ()->new ClassificacaoDocumentalDTO("não identificado","baixa",List.of(),List.of(),"Classificação não disponível."));
        boolean rodarProcesso=opcoes.forcarProcesso()||classificacao.pareceProcesso(), rodarContrato=opcoes.forcarContrato()||classificacao.pareceContrato();
        if(!rodarProcesso&&!rodarContrato){rodarProcesso=true;avisos.add("A classificação não indicou claramente processo nem contrato; a análise processual foi executada por padrão.");}

        String contextoDocumental=contextoAnterior(contexto,"Document Agent",classificacao); AnaliseProcessualDTO analiseProcessual;
        if(rodarProcesso){
            listener.updateRich(AnaliseEspecializadaStatus.ANALISANDO_PROCESSO,25,"Process Agent",2,TOTAL_AGENTES,"ANALYZING_PROCESS",
                    "Reconstruindo teses, riscos, fase processual e estratégia da parte representada.",List.of("Análise Base","Document Agent"),"Recebeu a classificação documental.");
            analiseProcessual=executar("Process Agent",agentesExecutados,avisos,()->processAgent.analisar(analiseBase,parteRepresentada,contextoDocumental,amostraTexto),
                    ()->AnaliseProcessualDTO.naoAplicavel("Process Agent não concluiu a análise."));
        }else analiseProcessual=AnaliseProcessualDTO.naoAplicavel("Material não classificado como processo judicial; use forcarProcesso=true para executar.");

        String contextoProcessual=contextoAnterior(contexto,"Document Agent",classificacao,"Process Agent",analiseProcessual); AnaliseContratualDTO analiseContratual;
        if(rodarContrato){
            listener.updateRich(AnaliseEspecializadaStatus.ANALISANDO_CONTRATO,40,"Contract Agent",3,TOTAL_AGENTES,"ANALYZING_CONTRACT",
                    "Mapeando cláusulas críticas, obrigações, multas, prazos e inconsistências.",List.of("Análise Base","Document Agent","Process Agent"),"Recebeu os relatórios anteriores para cruzamento.");
            analiseContratual=executar("Contract Agent",agentesExecutados,avisos,()->contractAgent.analisar(parteRepresentada,contextoProcessual,amostraTexto),
                    ()->AnaliseContratualDTO.naoAplicavel("Contract Agent não concluiu a análise."));
        }else analiseContratual=AnaliseContratualDTO.naoAplicavel("Material não classificado como contrato; use forcarContrato=true para executar.");

        String contextoContratual=contextoAnterior(contexto,"Document Agent",classificacao,"Process Agent",analiseProcessual,"Contract Agent",analiseContratual);
        listener.updateRich(AnaliseEspecializadaStatus.MAPEANDO_PRAZOS,55,"Deadline Agent",4,TOTAL_AGENTES,"MAPPING_DEADLINES",
                "Extraindo datas, eventos e prazos e comparando-os com as conclusões anteriores.",List.of("Análise Base","Document Agent","Process Agent","Contract Agent"),"Agenda será cruzada com os riscos identificados.");
        AgendaPrazosDTO agendaPrazos=executar("Deadline Agent",agentesExecutados,avisos,
                ()->deadlineAgent.montarAgenda(analiseBase,numeroProcesso,amostraTexto+"\n\nRELATÓRIOS ANTERIORES:\n"+contextoContratual),
                ()->new AgendaPrazosDTO(List.of(),List.of(),List.of(),"Agenda de prazos não disponível: o Deadline Agent falhou."));

        String contextoPrazos=contextoAnterior(contexto,"Document Agent",classificacao,"Process Agent",analiseProcessual,"Contract Agent",analiseContratual,"Deadline Agent",agendaPrazos);
        listener.updateRich(AnaliseEspecializadaStatus.CRUZANDO_EVIDENCIAS,65,"Evidence Agent",5,TOTAL_AGENTES,"CROSS_REFERENCING_EVIDENCE",
                "Relacionando alegações, documentos e conclusões anteriores e procurando lacunas probatórias.",List.of("Análise Base","Document Agent","Process Agent","Contract Agent","Deadline Agent"),"Recebeu quatro relatórios especializados anteriores.");
        MatrizEvidenciasDTO matrizEvidencias=executar("Evidence Agent",agentesExecutados,avisos,
                ()->evidenceAgent.relacionar(analiseBase,amostraTexto+"\n\nRELATÓRIOS ANTERIORES:\n"+contextoPrazos),
                ()->new MatrizEvidenciasDTO(List.of(),List.of(),List.of(),"Matriz de evidências não disponível: o Evidence Agent falhou."));

        String contextoEvidencias=contextoAnterior(contexto,"Document Agent",classificacao,"Process Agent",analiseProcessual,"Contract Agent",analiseContratual,"Deadline Agent",agendaPrazos,"Evidence Agent",matrizEvidencias);
        PesquisaJuridicaDTO pesquisaJuridica;
        if(opcoes.pesquisaJuridica()){
            listener.updateRich(AnaliseEspecializadaStatus.PESQUISANDO_FONTES,75,"Legal Research Agent",6,TOTAL_AGENTES,"LEGAL_RESEARCH",
                    "Identificando questões jurídicas e consultando somente fontes autorizadas.",List.of("Análise Base","Document Agent","Process Agent","Contract Agent","Deadline Agent","Evidence Agent"),"Pesquisa recebe os achados dos agentes anteriores.");
            String consulta=opcoes.consultaPesquisa()==null||opcoes.consultaPesquisa().isBlank()?legalResearchAgent.derivarConsulta(analiseBase.resumoProcesso(),analiseBase.pedidos()):opcoes.consultaPesquisa();
            final String consultaFinal=consulta+"\n\nCONTEXTO DOS AGENTES ANTERIORES:\n"+contextoEvidencias;
            pesquisaJuridica=executar("Legal Research Agent",agentesExecutados,avisos,()->legalResearchAgent.pesquisar(consultaFinal),
                    ()->PesquisaJuridicaDTO.desabilitada("Legal Research Agent falhou; nenhuma referência foi citada."));
            if(!pesquisaJuridica.pesquisaRealizada())avisos.add("Pesquisa jurídica não produziu referências verificadas: "+pesquisaJuridica.aviso());
        }else pesquisaJuridica=PesquisaJuridicaDTO.desabilitada("Pesquisa jurídica não solicitada nesta análise.");

        List<RascunhoDTO> rascunhos=new ArrayList<>(); List<TipoRascunho> solicitados=limitarRascunhos(opcoes.rascunhosSolicitados(),avisos);
        if(!solicitados.isEmpty()){
            listener.updateRich(AnaliseEspecializadaStatus.REDIGINDO_RASCUNHOS,85,"Drafting Agent",7,TOTAL_AGENTES,"DRAFTING",
                    "Consolidando os relatórios dos agentes anteriores para produzir os rascunhos solicitados.",List.of("Análise Base","Document Agent","Process Agent","Contract Agent","Deadline Agent","Evidence Agent","Legal Research Agent"),"Todos os achados disponíveis serão considerados.");
            ContextoCaso contextoCaso=new ContextoCaso(nomeArquivo,analiseBase.resumoProcesso(),classificacao,analiseProcessual,analiseContratual,agendaPrazos,matrizEvidencias,pesquisaJuridica);
            int maxChars=properties.especializada()==null?8000:Math.max(2000,properties.especializada().maxCharsRascunho());
            for(TipoRascunho tipo:solicitados){try{rascunhos.add(draftingAgent.redigir(tipo,contextoCaso,parteRepresentada,contexto,maxChars));}catch(RuntimeException e){log.warn("Drafting Agent falhou para {}: {}",tipo,e.getMessage());avisos.add("Rascunho de "+tipo.name()+" não foi gerado: "+mensagem(e));}}
            if(!rascunhos.isEmpty())agentesExecutados.add("Drafting Agent");
        }

        listener.updateRich(AnaliseEspecializadaStatus.PARECER_SENIOR,95,"Senior Lawyer Agent",8,TOTAL_AGENTES,"SENIOR_REVIEW",
                "Revisando criticamente todos os relatórios, procurando contradições, lacunas e consolidando o parecer final.",List.of("Análise Base","Document Agent","Process Agent","Contract Agent","Deadline Agent","Evidence Agent","Legal Research Agent","Drafting Agent"),"Recebeu o conjunto completo da análise especializada.");
        MaterialAgentes material=new MaterialAgentes(analiseBase.resumoProcesso(),classificacao,analiseProcessual,analiseContratual,agendaPrazos,matrizEvidencias,pesquisaJuridica,rascunhos.stream().map(RascunhoDTO::titulo).toList(),agentesExecutados,avisos);
        ParecerSeniorDTO parecerSenior=executar("Senior Lawyer Agent",agentesExecutados,avisos,()->seniorLawyerAgent.consolidar(nomeArquivo,parteRepresentada,material),
                ()->new ParecerSeniorDTO("Parecer não consolidado","",List.of(),List.of(),List.of(),List.of(),List.of("O Senior Lawyer Agent falhou; revise manualmente os resultados dos demais agentes."),"não avaliado","Consolidação final indisponível. Os resultados dos agentes especializados seguem no corpo da resposta."));
        MetadataDTO metadata=new MetadataDTO(nomeArquivo,textoExtraido==null?0:textoExtraido.length(),analiseBase.metadata()==null?0:analiseBase.metadata().quantidadeTrechosProcessados(),properties.ai().model(),Instant.now());
        return new AnaliseEspecializadaResponse(metadata,analiseBaseId,classificacao,analiseProcessual,analiseContratual,agendaPrazos,matrizEvidencias,pesquisaJuridica,List.copyOf(rascunhos),parecerSenior,List.copyOf(agentesExecutados),List.copyOf(avisos));
    }

    private String contextoAnterior(String contexto,Object...relatorios){StringBuilder out=new StringBuilder(contexto==null?"":contexto);for(int i=0;i<relatorios.length;i+=2)if(i+1<relatorios.length)out.append("\n\n===== ").append(relatorios[i]).append(" =====\n").append(relatorios[i+1]);String value=out.toString();return value.length()<=MAX_CONTEXTO_ANTERIOR?value:value.substring(0,MAX_CONTEXTO_ANTERIOR)+"\n[CONTEXTO ANTERIOR TRUNCADO]";}
    private List<TipoRascunho> limitarRascunhos(List<TipoRascunho> solicitados,List<String> avisos){int limite=properties.especializada()==null?5:properties.especializada().maxRascunhosOuPadrao();if(solicitados.size()<=limite)return solicitados;avisos.add("Foram solicitados "+solicitados.size()+" rascunhos; o limite configurado é "+limite+". Os excedentes foram ignorados.");return solicitados.subList(0,limite);}
    private String amostra(String texto){if(texto==null||texto.isBlank())return "não identificado";int limite=properties.especializada()==null?16000:properties.especializada().amostraTextoCharsOuPadrao();return texto.length()<=limite?texto:texto.substring(0,limite)+"\n[...texto truncado para a análise especializada...]";}
    private <T>T executar(String nomeAgente,List<String> executados,List<String> avisos,java.util.function.Supplier<T> acao,java.util.function.Supplier<T> fallback){try{T resultado=acao.get();executados.add(nomeAgente);return resultado;}catch(RuntimeException e){log.warn("{} falhou: {}",nomeAgente,e.getMessage());avisos.add(nomeAgente+" não concluiu: "+mensagem(e));return fallback.get();}}
    private String mensagem(RuntimeException e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private record ContextoCaso(String nomeArquivo,String resumoBase,ClassificacaoDocumentalDTO classificacao,AnaliseProcessualDTO analiseProcessual,AnaliseContratualDTO analiseContratual,AgendaPrazosDTO agendaPrazos,MatrizEvidenciasDTO matrizEvidencias,PesquisaJuridicaDTO pesquisaJuridica){}
    private record MaterialAgentes(String resumoBase,ClassificacaoDocumentalDTO classificacaoDocumental,AnaliseProcessualDTO analiseProcessual,AnaliseContratualDTO analiseContratual,AgendaPrazosDTO agendaPrazos,MatrizEvidenciasDTO matrizEvidencias,PesquisaJuridicaDTO pesquisaJuridica,List<String> rascunhosGerados,List<String> agentesExecutados,List<String> avisos){}
}
