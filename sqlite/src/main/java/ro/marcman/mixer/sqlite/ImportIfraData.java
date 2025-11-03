package ro.marcman.mixer.sqlite;

/**
 * Simple wrapper to import IFRA data.
 * Run this class to populate the database with IFRA ingredients.
 */
public class ImportIfraData {
    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("IFRA Data Import - MarcmanMixer");
        System.out.println("============================================================");
        System.out.println();
        
        String csvPath = args.length > 0 ? args[0] : "data/ifra_ingredients_to_import.csv";
        
        System.out.println("CSV file: " + csvPath);
        System.out.println();
        
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            IngredientRepositoryImpl repository = new IngredientRepositoryImpl(dbManager);
            IfraDataImporter importer = new IfraDataImporter(repository);
            
            System.out.println("Starting import...");
            int imported = importer.importFromCsv(csvPath);
            
            System.out.println();
            System.out.println("============================================================");
            System.out.println("SUCCESS! Imported " + imported + " ingredients from IFRA Transparency List");
            System.out.println("============================================================");
            System.out.println();
            System.out.println("Database file: marcman_mixer.db");
            System.out.println();
            
            dbManager.close();
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("ERROR during import: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}



