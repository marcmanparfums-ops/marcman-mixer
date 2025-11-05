package ro.marcman.mixer.core.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for parsing perfume recipes from PDF files
 */
@Slf4j
public class PdfRecipeParser {
    
    // Regex patterns for ingredient identification
    private static final Pattern CAS_PATTERN = Pattern.compile("\\b(\\d{2,7}-\\d{2}-\\d)\\b");
    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s*%");
    private static final Pattern PERCENTAGE_DECIMAL_PATTERN = Pattern.compile("^\\s*(\\d+\\.\\d{2})\\s+"); // Detect "6.00 " format with decimal point
    private static final Pattern PERCENTAGE_SPACE_PATTERN = Pattern.compile("^\\s*(\\d+)\\s+(\\d{2})\\s+"); // Detect "6 00 " format with space
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s*(ml|g|mg|drops?|grams?)");
    
    /**
     * Represents an ingredient extracted from PDF
     */
    public static class PdfIngredient {
        private String rawText;
        private String casNumber;
        private String name;
        private Double percentage;
        private Double amount;
        private String unit;
        private int lineNumber;
        
        public PdfIngredient(String rawText, int lineNumber) {
            this.rawText = rawText;
            this.lineNumber = lineNumber;
            parseIngredient();
        }
        
        private void parseIngredient() {
            String workingText = rawText;
            System.out.println("[DEBUG] PARSING: \"" + rawText + "\"");
            
            // Extract percentage in "X.XX " format (e.g., "6.00 " = 6.00%) - PRIORITY 1
            Matcher percentDecimalMatcher = PERCENTAGE_DECIMAL_PATTERN.matcher(workingText);
            if (percentDecimalMatcher.find()) {
                try {
                    this.percentage = Double.parseDouble(percentDecimalMatcher.group(1));
                    // Remove from working text
                    workingText = workingText.substring(percentDecimalMatcher.end());
                    System.out.println("[DEBUG] MATCHED DECIMAL: " + this.percentage + "% | Remaining: \"" + workingText + "\"");
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse decimal percentage: {}", percentDecimalMatcher.group(1));
                }
            } else {
                // Extract percentage in "X YY " format (e.g., "6 00 " = 6.00%) - PRIORITY 2
                Matcher percentSpaceMatcher = PERCENTAGE_SPACE_PATTERN.matcher(workingText);
                if (percentSpaceMatcher.find()) {
                    try {
                        String wholePart = percentSpaceMatcher.group(1);
                        String decimalPart = percentSpaceMatcher.group(2);
                        this.percentage = Double.parseDouble(wholePart + "." + decimalPart);
                        // Remove from working text
                        workingText = workingText.substring(percentSpaceMatcher.end());
                        System.out.println("[DEBUG] MATCHED SPACE: " + this.percentage + "% | Remaining: \"" + workingText + "\"");
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse space-separated percentage: {} {}", 
                                percentSpaceMatcher.group(1), percentSpaceMatcher.group(2));
                    }
                } else {
                    // Try standard percentage format with % - PRIORITY 3
                    Matcher percentMatcher = PERCENTAGE_PATTERN.matcher(workingText);
                    if (percentMatcher.find()) {
                        try {
                            this.percentage = Double.parseDouble(percentMatcher.group(1));
                            workingText = workingText.replace(percentMatcher.group(0), " ");
                            System.out.println("[DEBUG] MATCHED PERCENT SIGN: " + this.percentage + "%");
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse percentage: {}", percentMatcher.group(1));
                        }
                    }
                }
            }
            
            // Extract CAS number
            Matcher casMatcher = CAS_PATTERN.matcher(workingText);
            if (casMatcher.find()) {
                this.casNumber = casMatcher.group(1);
                workingText = workingText.replace(casMatcher.group(0), " ");
            }
            
            // Extract amount and unit
            Matcher amountMatcher = AMOUNT_PATTERN.matcher(workingText);
            if (amountMatcher.find()) {
                try {
                    this.amount = Double.parseDouble(amountMatcher.group(1));
                    this.unit = amountMatcher.group(2);
                    workingText = workingText.replace(amountMatcher.group(0), " ");
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse amount: {}", amountMatcher.group(1));
                }
            }
            
            // Extract name (clean up remaining text)
            String cleanName = workingText;
            cleanName = cleanName.replaceAll("[^a-zA-Z0-9\\s\\-()]", " ");
            cleanName = cleanName.trim().replaceAll("\\s+", " ");
            
            this.name = cleanName.isEmpty() ? "Unknown" : cleanName;
            System.out.println("[DEBUG] PARSED NAME: \"" + this.name + "\" | PERCENTAGE: " + this.percentage);
        }
        
        // Getters
        public String getRawText() { return rawText; }
        public String getCasNumber() { return casNumber; }
        public String getName() { return name; }
        public Double getPercentage() { return percentage; }
        public Double getAmount() { return amount; }
        public String getUnit() { return unit; }
        public int getLineNumber() { return lineNumber; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Line ").append(lineNumber).append(": ");
            sb.append(name);
            if (casNumber != null) sb.append(" (CAS: ").append(casNumber).append(")");
            if (percentage != null) sb.append(" - ").append(percentage).append("%");
            if (amount != null) sb.append(" - ").append(amount).append(" ").append(unit);
            return sb.toString();
        }
    }
    
    /**
     * Extract text from PDF file
     */
    public String extractText(File pdfFile) throws IOException {
        log.info("Extracting text from PDF: {}", pdfFile.getName());
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF", text.length());
            return text;
        }
    }
    
    /**
     * Parse ingredients from PDF text
     */
    public List<PdfIngredient> parseIngredients(String pdfText) {
        log.info("Parsing ingredients from PDF text");
        
        List<PdfIngredient> ingredients = new ArrayList<>();
        String[] lines = pdfText.split("\\r?\\n");
        
        // Parse each line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // Skip empty lines and headers
            if (line.isEmpty() || line.length() < 3) {
                continue;
            }
            
            // Skip common headers
            if (isHeaderLine(line)) {
                continue;
            }
            
            // Skip lines that are ONLY a CAS number (no ingredient name)
            if (line.matches("^\\s*\\d{2,7}-\\d{2}-\\d\\s*$")) {
                log.debug("Skipping standalone CAS number: {}", line);
                continue;
            }
            
            // Ingredient MUST have percentage OR amount (CAS number is optional)
            // This ensures we only capture actual recipe ingredients, not just text
            boolean hasPercentage = PERCENTAGE_PATTERN.matcher(line).find() ||
                                    PERCENTAGE_DECIMAL_PATTERN.matcher(line).find() ||
                                    PERCENTAGE_SPACE_PATTERN.matcher(line).find();
            boolean hasAmount = AMOUNT_PATTERN.matcher(line).find();
            
            if (hasPercentage || hasAmount) {
                PdfIngredient ingredient = new PdfIngredient(line, i + 1);
                
                // Skip if name is "TOTAL" (summary line)
                if ("TOTAL".equalsIgnoreCase(ingredient.getName().trim())) {
                    log.debug("Skipping TOTAL line: {}", line);
                    continue;
                }
                
                // Double-check: ingredient must have either percentage or amount after parsing
                if ((ingredient.getPercentage() != null || ingredient.getAmount() != null) &&
                    !"Unknown".equals(ingredient.getName())) {
                    ingredients.add(ingredient);
                    log.debug("Found ingredient: {}", ingredient);
                } else {
                    log.debug("Skipping ingredient without percentage/amount: {}", line);
                }
            }
        }
        
        log.info("Parsed {} ingredients from PDF", ingredients.size());
        return ingredients;
    }
    
    /**
     * Parse ingredients directly from PDF file
     */
    public List<PdfIngredient> parseIngredientsFromFile(File pdfFile) throws IOException {
        String text = extractText(pdfFile);
        return parseIngredients(text);
    }
    
    /**
     * Check if line is a common header
     */
    private boolean isHeaderLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("ingredient") && lower.contains("list") ||
               lower.contains("formula") && lower.contains("composition") ||
               lower.contains("component") ||
               lower.matches("^(name|ingredient|cas|amount|percentage|%|quantity)\\s*$") ||
               lower.startsWith("page ") ||
               lower.matches("^\\d+\\s*$") ||
               lower.trim().startsWith("total");
    }
}

