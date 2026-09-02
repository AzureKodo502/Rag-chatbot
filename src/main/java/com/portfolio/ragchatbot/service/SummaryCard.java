package com.portfolio.ragchatbot.service;

import java.util.List;

/**
 * Scheda di sintesi del profilo ("Genera Riassunto").
 *
 * Sezioni STATICHE (curate a mano, da {@code summary.json}): inBreve,
 * puntiDiForza, doveStaCrescendo, logistica, contatto.
 * Sezioni GENERATE dall'LLM sulla knowledge base: formazione, progetti, stack.
 */
public record SummaryCard(
        String lang,
        boolean generatedFresh,
        String inBreve,
        String formazione,
        List<Section> puntiDiForza,
        List<Section> doveStaCrescendo,
        List<Project> progetti,
        String stack,
        String logistica,
        String contatto
) {
    public record Section(String titolo, String testo) {
    }

    public record Project(String nome, String descrizione) {
    }
}
