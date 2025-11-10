package ro.marcman.mixer.core.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzePdf {
    private static final Pattern CAS_PATTERN = Pattern.compile("\\b(\\d{2,7}-\\d{2}-\\d)\\b");
    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s*%");
    private static final Pattern PERCENTAGE_DECIMAL_PATTERN = Pattern.compile("^\\s*(\\d+\\.\\d{2})\\s+");
    private static final Pattern PERCENTAGE_SPACE_PATTERN = Pattern.compile("^\\s*(\\d+)\\s+(\\d{2})\\s+");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s*(ml|g|mg|drops?|grams?)");
    
    public static void main(String[] args) {
        try {
            File pdfFile = new File("C:\\Users\\Marcman\\Desktop\\test1.pdf");
            
            if (!pdfFile.exists()) {
                System.out.println("PDF file not found: " + pdfFile.getAbsolutePath());
                return;
            }
            
            System.out.println("==============================================");
            System.out.println("ANALIZĂ DETALIATĂ PDF - test1.pdf");
            System.out.println("==============================================\n");
            System.out.println("File: " + pdfFile.getName());
            System.out.println("Size: " + pdfFile.length() + " bytes\n");
            
            // Extract text
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                
                System.out.println("Text extras: " + text.length() + " caractere\n");
                System.out.println("=== TOATE LINIILE ===\n");
                
                String[] lines = text.split("\\r?\\n");
                int ingredientCount = 0;
                int rejectedCount = 0;
                
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    
                    if (line.isEmpty() || line.length() < 3) {
                        System.out.printf("%3d: [GOALĂ] %s%n", i+1, line.isEmpty() ? "(linie goală)" : line);
                        continue;
                    }
                    
                    // Check if header
                    String lower = line.toLowerCase();
                    boolean isHeader = lower.contains("ingredient") && lower.contains("list") ||
                                     lower.contains("formula") && lower.contains("composition") ||
                                     lower.contains("component") ||
                                     lower.matches("^(name|ingredient|cas|amount|percentage|%|quantity)\\s*$") ||
                                     lower.startsWith("page ") ||
                                     lower.matches("^\\d+\\s*$") ||
                                     lower.trim().startsWith("total");
                    
                    if (isHeader) {
                        System.out.printf("%3d: [HEADER] %s%n", i+1, line);
                        continue;
                    }
                    
                    // Check if only CAS number
                    if (line.matches("^\\s*\\d{2,7}-\\d{2}-\\d\\s*$")) {
                        System.out.printf("%3d: [DOAR CAS] %s%n", i+1, line);
                        continue;
                    }
                    
                    // Check for percentage or amount
                    boolean hasPercentage = PERCENTAGE_PATTERN.matcher(line).find() ||
                                            PERCENTAGE_DECIMAL_PATTERN.matcher(line).find() ||
                                            PERCENTAGE_SPACE_PATTERN.matcher(line).find();
                    boolean hasAmount = AMOUNT_PATTERN.matcher(line).find();
                    
                    if (hasPercentage || hasAmount) {
                        ingredientCount++;
                        System.out.printf("%3d: [✓ ACCEPTAT] %s%n", i+1, line);
                        System.out.println("     → Procentaj: " + (hasPercentage ? "DA" : "NU"));
                        System.out.println("     → Cantitate: " + (hasAmount ? "DA" : "NU"));
                        
                        // Parse details
                        parseLineDetails(line, i+1);
                    } else {
                        rejectedCount++;
                        System.out.printf("%3d: [✗ RESPINS] %s%n", i+1, line);
                        System.out.println("     → MOTIV: Fără procentaj sau cantitate recunoscută");
                        System.out.println("     → Analiză detaliată:");
                        
                        // Try to find what's missing
                        Matcher casMatch = CAS_PATTERN.matcher(line);
                        if (casMatch.find()) {
                            System.out.println("     → CAS găsit: " + casMatch.group(1));
                        } else {
                            System.out.println("     → CAS: NU");
                        }
                        
                        // Check for any numbers
                        Pattern numberPattern = Pattern.compile("\\d+");
                        Matcher numberMatch = numberPattern.matcher(line);
                        boolean foundNumber = false;
                        while (numberMatch.find()) {
                            if (!foundNumber) {
                                System.out.println("     → Numere găsite: " + numberMatch.group());
                                foundNumber = true;
                            } else {
                                System.out.println("     →              " + numberMatch.group());
                            }
                        }
                        if (!foundNumber) {
                            System.out.println("     → Numere: NU");
                        }
                        
                        // Show what formats were tried
                        System.out.println("     → Formate testate:");
                        System.out.println("        • X.XX / X.XX% / X.XX : " + (PERCENTAGE_DECIMAL_PATTERN.matcher(line).find() ? "DA" : "NU"));
                        System.out.println("        • X YY (spațiu): " + (PERCENTAGE_SPACE_PATTERN.matcher(line).find() ? "DA" : "NU"));
                        System.out.println("        • X% / X.XX%: " + (PERCENTAGE_PATTERN.matcher(line).find() ? "DA" : "NU"));
                        System.out.println("        • Xml / Xg / Xmg: " + (AMOUNT_PATTERN.matcher(line).find() ? "DA" : "NU"));
                    }
                    System.out.println();
                }
                
                System.out.println("==============================================");
                System.out.println("REZUMAT:");
                System.out.println("  Total linii: " + lines.length);
                System.out.println("  Ingrediente acceptate: " + ingredientCount);
                System.out.println("  Ingrediente respinse: " + rejectedCount);
                System.out.println("==============================================");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void parseLineDetails(String line, int lineNum) {
        System.out.println("     → Detalii parsing:");
        
        // Check percentage formats
        Matcher percentDecimal = PERCENTAGE_DECIMAL_PATTERN.matcher(line);
        if (percentDecimal.find()) {
            System.out.println("        • Procentaj zecimal: " + percentDecimal.group(1) + "%");
        }
        
        Matcher percentSpace = PERCENTAGE_SPACE_PATTERN.matcher(line);
        if (percentSpace.find()) {
            System.out.println("        • Procentaj cu spațiu: " + percentSpace.group(1) + " " + percentSpace.group(2));
        }
        
        Matcher percent = PERCENTAGE_PATTERN.matcher(line);
        if (percent.find()) {
            System.out.println("        • Procentaj standard: " + percent.group(1) + "%");
        }
        
        // Check amount
        Matcher amount = AMOUNT_PATTERN.matcher(line);
        if (amount.find()) {
            System.out.println("        • Cantitate: " + amount.group(1) + " " + amount.group(2));
        }
        
        // Check CAS
        Matcher cas = CAS_PATTERN.matcher(line);
        if (cas.find()) {
            System.out.println("        • CAS: " + cas.group(1));
        }
    }
}


