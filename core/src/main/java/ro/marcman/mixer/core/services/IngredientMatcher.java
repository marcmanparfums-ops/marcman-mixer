package ro.marcman.mixer.core.services;

import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for matching parsed PDF ingredients with database ingredients
 */
@Slf4j
public class IngredientMatcher {
    
    /**
     * Represents a matched ingredient with confidence score
     */
    public static class MatchedIngredient {
        private final PdfRecipeParser.PdfIngredient pdfIngredient;
        private final Ingredient dbIngredient;
        private final MatchType matchType;
        private final double confidence;
        
        public enum MatchType {
            EXACT_CAS,      // Exact CAS number match (100% confidence)
            EXACT_NAME,     // Exact name match (95% confidence)
            FUZZY_NAME,     // Fuzzy name match (70% confidence)
            NO_MATCH        // No match found (0% confidence)
        }
        
        public MatchedIngredient(PdfRecipeParser.PdfIngredient pdfIngredient, 
                                Ingredient dbIngredient, 
                                MatchType matchType, 
                                double confidence) {
            this.pdfIngredient = pdfIngredient;
            this.dbIngredient = dbIngredient;
            this.matchType = matchType;
            this.confidence = confidence;
        }
        
        public PdfRecipeParser.PdfIngredient getPdfIngredient() { return pdfIngredient; }
        public Ingredient getDbIngredient() { return dbIngredient; }
        public MatchType getMatchType() { return matchType; }
        public double getConfidence() { return confidence; }
        public boolean isMatched() { return matchType != MatchType.NO_MATCH; }
        
        public String getSuggestedName() {
            return dbIngredient != null ? dbIngredient.getName() : pdfIngredient.getName();
        }
        
        public String getStatusText() {
            if (dbIngredient == null) {
                return "⚠ Not found in database";
            }
            return switch (matchType) {
                case EXACT_CAS -> "✓ Exact CAS match";
                case EXACT_NAME -> "✓ Exact name match";
                case FUZZY_NAME -> "~ Fuzzy name match";
                case NO_MATCH -> "✗ No match";
            };
        }
    }
    
    /**
     * Match a single PDF ingredient with database ingredients
     * STRICT: Only allows exact CAS matches when CAS numbers are present
     */
    public MatchedIngredient matchIngredient(PdfRecipeParser.PdfIngredient pdfIngredient, 
                                            List<Ingredient> allIngredients) {
        
        boolean hasPdfCas = pdfIngredient.getCasNumber() != null && !pdfIngredient.getCasNumber().trim().isEmpty();
        
        // First, try exact CAS match (most reliable)
        if (hasPdfCas) {
            for (Ingredient ing : allIngredients) {
                String dbCas = ing.getCasNumber();
                if (dbCas != null && !dbCas.trim().isEmpty() && 
                    pdfIngredient.getCasNumber().equalsIgnoreCase(dbCas)) {
                    // CAS matches - return this as EXACT_CAS match
                    log.debug("Exact CAS match: '{}' (CAS: {}) -> '{}'", 
                             pdfIngredient.getName(), pdfIngredient.getCasNumber(), ing.getName());
                    return new MatchedIngredient(pdfIngredient, ing, MatchedIngredient.MatchType.EXACT_CAS, 1.0);
                }
            }
            // If we have CAS but no match found, DO NOT fall through to name matching
            log.warn("No CAS match found for: '{}' (CAS: {})", 
                    pdfIngredient.getName(), pdfIngredient.getCasNumber());
            return new MatchedIngredient(pdfIngredient, null, MatchedIngredient.MatchType.NO_MATCH, 0.0);
        }
        
        // Second, try exact name match (case-insensitive) - ONLY if no CAS in PDF
        String pdfName = pdfIngredient.getName().toLowerCase().trim();
        for (Ingredient ing : allIngredients) {
            String dbName = ing.getName() != null ? ing.getName().toLowerCase().trim() : "";
            if (!dbName.isEmpty() && pdfName.equals(dbName)) {
                log.debug("✓ Exact name match: '{}' -> '{}'", pdfIngredient.getName(), ing.getName());
                return new MatchedIngredient(pdfIngredient, ing, MatchedIngredient.MatchType.EXACT_NAME, 0.95);
            }
        }
        
        // Third, try fuzzy name match (contains or partial match) - ONLY if no CAS in PDF
        Ingredient bestMatch = null;
        double bestScore = 0.0;
        
        for (Ingredient ing : allIngredients) {
            double score = calculateSimilarity(pdfName, ing.getName().toLowerCase());
            if (score > bestScore && score > 0.6) { // Minimum 60% similarity
                bestScore = score;
                bestMatch = ing;
            }
        }
        
        if (bestMatch != null) {
            log.debug("Fuzzy match: {} -> {} (score: {:.2f})", pdfName, bestMatch.getName(), bestScore);
            return new MatchedIngredient(pdfIngredient, bestMatch, MatchedIngredient.MatchType.FUZZY_NAME, bestScore);
        }
        
        // No match found
        log.warn("No match found for: {}", pdfIngredient.getName());
        return new MatchedIngredient(pdfIngredient, null, MatchedIngredient.MatchType.NO_MATCH, 0.0);
    }
    
    /**
     * Match multiple PDF ingredients with database
     */
    public List<MatchedIngredient> matchIngredients(List<PdfRecipeParser.PdfIngredient> pdfIngredients,
                                                    List<Ingredient> allIngredients) {
        log.info("Matching {} PDF ingredients with {} database ingredients", 
                 pdfIngredients.size(), allIngredients.size());
        
        List<MatchedIngredient> matches = new ArrayList<>();
        for (PdfRecipeParser.PdfIngredient pdfIng : pdfIngredients) {
            matches.add(matchIngredient(pdfIng, allIngredients));
        }
        
        long exactMatches = matches.stream().filter(m -> m.getMatchType() == MatchedIngredient.MatchType.EXACT_CAS || 
                                                         m.getMatchType() == MatchedIngredient.MatchType.EXACT_NAME).count();
        long fuzzyMatches = matches.stream().filter(m -> m.getMatchType() == MatchedIngredient.MatchType.FUZZY_NAME).count();
        long noMatches = matches.stream().filter(m -> m.getMatchType() == MatchedIngredient.MatchType.NO_MATCH).count();
        
        log.info("Match results: {} exact, {} fuzzy, {} not found", exactMatches, fuzzyMatches, noMatches);
        
        return matches;
    }
    
    /**
     * Calculate similarity between two strings (Levenshtein-based)
     * Note: If strings are exactly equal, they should already be matched as EXACT_NAME before this function is called
     */
    private double calculateSimilarity(String s1, String s2) {
        // If strings are exactly equal, return 1.0 (shouldn't reach here but just in case)
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        // Calculate Levenshtein distance for better matching
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) distance / maxLen);
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
}


