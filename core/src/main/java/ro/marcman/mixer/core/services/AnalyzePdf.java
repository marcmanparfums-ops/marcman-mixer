package ro.marcman.mixer.core.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzePdf {
    public static void main(String[] args) {
        try {
            File pdfFile = new File("C:\\Users\\Marcman\\Desktop\\152B_A_Men Fantasm Type Formula - Creative Formulas.pdf");
            
            if (!pdfFile.exists()) {
                System.out.println("PDF file not found: " + pdfFile.getAbsolutePath());
                return;
            }
            
            System.out.println("==============================================");
            System.out.println("PDF STRUCTURE ANALYSIS");
            System.out.println("==============================================\n");
            System.out.println("File: " + pdfFile.getName());
            System.out.println("Size: " + pdfFile.length() + " bytes\n");
            
            // Extract text
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                
                System.out.println("Extracted " + text.length() + " characters\n");
                System.out.println("=== FIRST 50 LINES ===");
                System.out.println("----------------------------------------------");
                
                String[] lines = text.split("\\r?\\n");
                int count = 0;
                
                for (int i = 0; i < Math.min(50, lines.length); i++) {
                    String line = lines[i];
                    if (!line.trim().isEmpty()) {
                        count++;
                        System.out.printf("%3d: %s%n", i, line);
                        
                        // Test regex on each line
                        Pattern PERCENTAGE_SPACE_PATTERN = Pattern.compile("^\\s*(\\d+)\\s+(\\d{2})\\s+");
                        Matcher matcher = PERCENTAGE_SPACE_PATTERN.matcher(line);
                        if (matcher.find()) {
                            String wholePart = matcher.group(1);
                            String decimalPart = matcher.group(2);
                            double percentage = Double.parseDouble(wholePart + "." + decimalPart);
                            String remaining = line.substring(matcher.end());
                            System.out.println("     ✓ MATCHED: " + percentage + "% | Remaining: \"" + remaining + "\"");
                        }
                    }
                }
                
                System.out.println("----------------------------------------------");
                System.out.println("\nTotal lines: " + lines.length);
                System.out.println("Non-empty lines shown: " + count);
                
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


