package com.portfolio.ragchatbot.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class PromptGuardService {

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignora\\s+.{0,25}istruzioni", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+.{0,25}instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("non\\s+sei\\s+pi[uù]\\s+.{0,30}(assistente|chatbot)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sei\\s+(ora|adesso)\\s+(un|una)\\s+.{0,20}(chatbot|assistente|bot|agente|ai)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+(a|an)\\s+.{0,20}(chatbot|assistant|bot|agent|ai)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(ripeti|rivela|mostra|stampa|fornisci|dammi|dimmi|condividi|elenca)\\s+.{0,25}(prompt|istruzioni)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(repeat|reveal|show|print|give|tell|share)\\s+.{0,25}(system\\s*prompt|your\\s+instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dev\\s*mode|developer\\s*mode|jailbreak|DAN\\s*mode", Pattern.CASE_INSENSITIVE)
    );

    public static final String REFUSAL_MESSAGE =
            "Il messaggio contiene un pattern che assomiglia a un tentativo di modificare il mio comportamento. "
                    + "Sono qui solo per rispondere a domande sul percorso professionale, le competenze tecniche "
                    + "e i progetti software del candidato — fammi pure una domanda in merito!";

    public boolean isSuspicious(String message) {
        return SUSPICIOUS_PATTERNS.stream().anyMatch(p -> p.matcher(message).find());
    }
}
