package ro.marcman.mixer.sqlite;

import java.io.File;
import java.sql.Connection;

/**
 * Test to verify database creation at correct location
 */
public class TestDatabaseCreation {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Test Creare Baza de Date");
        System.out.println("========================================");
        System.out.println();
        
        // Get expected path
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isEmpty()) {
            String userHome = System.getProperty("user.home");
            localAppData = userHome + File.separator + "AppData" + File.separator + "Local";
        }
        
        String expectedPath = localAppData + File.separator + "MarcmanMixer" + File.separator + "marcman_mixer.db";
        File expectedFile = new File(expectedPath);
        
        System.out.println("Locația așteptată: " + expectedPath);
        System.out.println();
        
        System.out.println("Înainte de inițializare:");
        System.out.println("  Folder există: " + expectedFile.getParentFile().exists());
        System.out.println("  Fișier există: " + expectedFile.exists());
        System.out.println();
        
        try {
            System.out.println("Creare DatabaseManager...");
            DatabaseManager dbManager = DatabaseManager.getInstance();
            
            String actualPath = dbManager.getDatabasePath();
            System.out.println("Calea returnată: " + actualPath);
            System.out.println("Cale așteptată:  " + expectedPath);
            System.out.println("Căile coincid: " + actualPath.equals(expectedPath));
            System.out.println();
            
            System.out.println("Creare conexiune...");
            Connection conn = dbManager.getConnection();
            System.out.println("Conexiune creată: " + (conn != null && !conn.isClosed()));
            System.out.println();
            
            System.out.println("După inițializare:");
            System.out.println("  Folder există: " + expectedFile.getParentFile().exists());
            System.out.println("  Fișier există: " + expectedFile.exists());
            
            if (expectedFile.exists()) {
                System.out.println("  Dimensiune fișier: " + expectedFile.length() + " bytes");
                System.out.println("  [SUCCESS] Baza de date a fost creată!");
            } else {
                System.out.println("  [ERROR] Baza de date NU a fost creată!");
                System.out.println("  Verificare folder părinte:");
                File parent = expectedFile.getParentFile();
                System.out.println("    Există: " + parent.exists());
                System.out.println("    Cale: " + parent.getAbsolutePath());
                if (parent.exists()) {
                    System.out.println("    Poate scrie: " + parent.canWrite());
                    System.out.println("    Lista fișiere:");
                    String[] files = parent.list();
                    if (files != null) {
                        for (String f : files) {
                            System.out.println("      - " + f);
                        }
                    }
                }
            }
            
            if (conn != null) {
                conn.close();
            }
            dbManager.close();
            
            System.out.println();
            System.out.println("========================================");
            if (expectedFile.exists()) {
                System.out.println("[SUCCESS] Test completat cu succes!");
            } else {
                System.out.println("[ERROR] Test eșuat - baza de date nu a fost creată!");
            }
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("========================================");
            System.err.println("[ERROR] Eroare la test:");
            System.err.println("========================================");
            e.printStackTrace();
            System.exit(1);
        }
    }
}

