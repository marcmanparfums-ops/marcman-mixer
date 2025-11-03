package ro.marcman.mixer.sqlite;

import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.ports.repository.IngredientRepository;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to import IFRA Transparency List data from CSV into database.
 * 
 * CSV format: cas_number,name,ifra_naturals_category,category,description
 */
@Slf4j
public class IfraDataImporter {
    
    private final IngredientRepository repository;
    
    public IfraDataImporter(IngredientRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Import ingredients from IFRA CSV file.
     * 
     * @param csvFilePath Path to CSV file
     * @return Number of ingredients imported
     */
    public int importFromCsv(String csvFilePath) {
        log.info("Starting IFRA data import from: {}", csvFilePath);
        
        Path path = Paths.get(csvFilePath);
        if (!Files.exists(path)) {
            log.error("CSV file not found: {}", csvFilePath);
            return 0;
        }
        
        int imported = 0;
        int skipped = 0;
        int errors = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            // Skip header line
            String header = reader.readLine();
            log.debug("CSV header: {}", header);
            
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (importLine(line)) {
                        imported++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Error importing line: {}", line, e);
                }
            }
            
        } catch (IOException e) {
            log.error("Error reading CSV file", e);
            return 0;
        }
        
        log.info("IFRA import completed: {} imported, {} skipped, {} errors", imported, skipped, errors);
        return imported;
    }
    
    private boolean importLine(String line) {
        // Parse CSV line
        String[] parts = parseCsvLine(line);
        if (parts.length < 3) {
            log.warn("Invalid CSV line (too few columns): {}", line);
            return false;
        }
        
        String name = parts[0].trim();  // FIXED: First column is NAME
        String casNumber = parts[1].trim();  // FIXED: Second column is CAS NUMBER
        String category = parts.length > 2 ? parts[2].trim() : "";
        String ifraNaturalsCategory = parts.length > 3 ? parts[3].trim() : "";
        String description = parts.length > 4 ? parts[4].trim() : "";
        
        // Skip if CAS number or name is empty
        if (casNumber.isEmpty() || name.isEmpty()) {
            log.debug("Skipping line with empty CAS or name: {}", line);
            return false;
        }
        
        // Create ingredient
        Ingredient ingredient = Ingredient.builder()
                .name(name)
                .casNumber(casNumber)
                .ifraNaturalsCategory(ifraNaturalsCategory.isEmpty() ? null : ifraNaturalsCategory)
                .ifraStatus("IFRA Approved")
                .category(category.isEmpty() ? "Uncategorized" : category)
                .description(description.isEmpty() ? null : description)
                .active(true)
                .build();
        
        try {
            repository.save(ingredient);
            log.debug("Imported: {} (CAS: {})", name, casNumber);
            return true;
        } catch (Exception e) {
            // Might be duplicate CAS number
            log.warn("Failed to import {} (CAS: {}): {}", name, casNumber, e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse CSV line handling quoted fields and commas within quotes.
     */
    private String[] parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        
        // Add last field
        result.add(field.toString());
        
        return result.toArray(new String[0]);
    }
    
    /**
     * Main method for standalone import.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java IfraDataImporter <csv_file_path>");
            System.out.println("Example: java IfraDataImporter data/ifra_ingredients_sample.csv");
            System.exit(1);
        }
        
        String csvPath = args[0];
        
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            IngredientRepository repository = new IngredientRepositoryImpl(dbManager);
            IfraDataImporter importer = new IfraDataImporter(repository);
            
            int imported = importer.importFromCsv(csvPath);
            System.out.println("Successfully imported " + imported + " ingredients from IFRA Transparency List");
            
            dbManager.close();
        } catch (Exception e) {
            System.err.println("Error during import: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}


