package ro.marcman.mixer.sqlite;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connection and initialization.
 * Singleton pattern to ensure only one database instance throughout the application.
 */
@Slf4j
public class DatabaseManager {
    
    private static volatile DatabaseManager instance;
    private static final String DB_PATH;
    private static final String DB_DIR;
    private static final String DB_URL;
    
    static {
        // Initialize static paths once when class is loaded
        DB_PATH = findDatabasePath();
        DB_DIR = new File(DB_PATH).getParent();
        DB_URL = "jdbc:sqlite:" + DB_PATH;
    }
    
    private Connection connection;
    
    /**
     * Find the correct database path regardless of how the application is launched
     */
    private static String findDatabasePath() {
        String userDir = System.getProperty("user.dir");
        File currentDir = new File(userDir);
        
        // Priority 1: Check if we're in MarcmanMixer_Portable directory (portable package)
        // This is the most common case when running from portable package
        if (currentDir.getName().equals("MarcmanMixer_Portable")) {
            File dbInPortable = new File(currentDir, "app" + File.separator + "marcman_mixer.db");
            if (dbInPortable.exists()) {
                return dbInPortable.getAbsolutePath();
            }
            // If not found but we're in portable package, create it here
            return dbInPortable.getAbsolutePath();
        }
        
        // Priority 2: Check if we're in app directory
        if (currentDir.getName().equals("app")) {
            File dbInApp = new File(currentDir, "marcman_mixer.db");
            if (dbInApp.exists()) {
                return dbInApp.getAbsolutePath();
            }
            // Create in app directory
            return dbInApp.getAbsolutePath();
        }
        
        // Priority 3: Check if app/marcman_mixer.db exists at current level
        File dbFileCurrent = new File(currentDir, "app" + File.separator + "marcman_mixer.db");
        if (dbFileCurrent.exists()) {
            return dbFileCurrent.getAbsolutePath();
        }
        
        // Priority 4: Try to find database by going up the directory tree
        // This handles: root, app/, dist/, or any subdirectory
        File searchDir = currentDir;
        int maxLevels = 5; // Limit search depth
        int level = 0;
        
        while (searchDir != null && level < maxLevels) {
            // Check if app/marcman_mixer.db exists at this level
            File dbFileSearch = new File(searchDir, "app" + File.separator + "marcman_mixer.db");
            if (dbFileSearch.exists()) {
                return dbFileSearch.getAbsolutePath();
            }
            
            // Check if we're in MarcmanMixer_Portable directory
            if (searchDir.getName().equals("MarcmanMixer_Portable")) {
                File dbInPortableUp = new File(searchDir, "app" + File.separator + "marcman_mixer.db");
                return dbInPortableUp.getAbsolutePath();
            }
            
            // Go up one level
            searchDir = searchDir.getParentFile();
            level++;
        }
        
        // Fallback: try standard locations
        // If we're running from the app directory (Maven javafx:run scenario)
        if (currentDir.getName().equals("app")) {
            File parentDir = currentDir.getParentFile();
            if (parentDir != null) {
                return parentDir.getAbsolutePath() + File.separator + "app" + File.separator + "marcman_mixer.db";
            }
        }
        
        // If we're in dist directory (executable scenario)
        if (currentDir.getName().equals("dist")) {
            File parentDir = currentDir.getParentFile();
            if (parentDir != null) {
                File dbFile = new File(parentDir, "app" + File.separator + "marcman_mixer.db");
                return dbFile.getAbsolutePath();
            }
        }
        
        // Normal case: running from root directory
        return currentDir.getAbsolutePath() + File.separator + "app" + File.separator + "marcman_mixer.db";
    }
    
    /**
     * Private constructor for singleton pattern
     */
    private DatabaseManager() {
        ensureDatabaseDirectoryExists();
    }
    
    /**
     * Get the singleton instance of DatabaseManager
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Ensure the database directory exists, create it if necessary
     */
    private void ensureDatabaseDirectoryExists() {
        try {
            File dbDir = new File(DB_DIR);
            if (!dbDir.exists()) {
                boolean created = dbDir.mkdirs();
                if (created) {
                    log.info("Created database directory: {}", DB_DIR);
                } else {
                    log.error("Failed to create database directory: {}", DB_DIR);
                }
            } else {
                log.debug("Database directory exists: {}", DB_DIR);
            }
            
            // Check write permissions
            if (!dbDir.canWrite()) {
                log.error("Database directory is not writable: {}", DB_DIR);
                throw new RuntimeException("Database directory is not writable: " + DB_DIR);
            }
        } catch (Exception e) {
            log.error("Error ensuring database directory exists", e);
            throw new RuntimeException("Failed to create database directory: " + DB_DIR, e);
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            initializeTables();
            // Enable WAL mode and force immediate writes for fresh data
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            } catch (SQLException e) {
                log.warn("Could not set PRAGMA settings", e);
            }
        }
        return connection;
    }
    
    /**
     * Force close current connection to ensure fresh data on next access
     */
    public void resetConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
                log.info("Database connection reset");
            }
        } catch (SQLException e) {
            log.warn("Error closing connection", e);
        }
    }
    
    private void initializeTables() {
        try (Statement stmt = connection.createStatement()) {
            // Ingredients table with IFRA data
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ingredients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    category TEXT,
                    cas_number TEXT,
                    ifra_naturals_category TEXT,
                    ifra_status TEXT,
                    arduino_uid TEXT,
                    arduino_pin INTEGER,
                    arduino_uid_small TEXT,
                    arduino_pin_small INTEGER,
                    default_duration INTEGER,
                    ms_per_gram_large INTEGER,
                    ms_per_gram_small INTEGER,
                    pump_threshold_grams REAL,
                    concentration REAL,
                    unit TEXT,
                    cost_per_unit REAL,
                    stock_quantity REAL,
                    supplier TEXT,
                    batch_number TEXT,
                    master_ingredient_id INTEGER,
                    active INTEGER DEFAULT 1,
                    FOREIGN KEY (master_ingredient_id) REFERENCES ingredients(id) ON DELETE SET NULL
                )
            """);
            
            // Recipes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recipes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    category TEXT,
                    created_at TEXT,
                    updated_at TEXT,
                    created_by TEXT,
                    batch_size INTEGER,
                    notes TEXT,
                    active INTEGER DEFAULT 1
                )
            """);
            
            // Recipe Ingredients table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recipe_ingredients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    recipe_id INTEGER NOT NULL,
                    ingredient_id INTEGER NOT NULL,
                    quantity REAL,
                    unit TEXT,
                    pulse_duration INTEGER,
                    sequence_order INTEGER,
                    notes TEXT,
                    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                    FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE
                )
            """);
            
            // Migrate existing tables to add new columns if they don't exist
            migrateIngredientsTable();
            
            log.info("Database tables initialized successfully");
        } catch (SQLException e) {
            log.error("Error initializing database tables", e);
        }
    }
    
    private void migrateIngredientsTable() {
        try (Statement stmt = connection.createStatement()) {
            // Check and add arduino_uid_small column
            try {
                stmt.execute("ALTER TABLE ingredients ADD COLUMN arduino_uid_small TEXT");
                log.info("Added column arduino_uid_small to ingredients table");
            } catch (SQLException e) {
                if (e.getMessage().contains("duplicate column")) {
                    log.debug("Column arduino_uid_small already exists");
                } else {
                    throw e;
                }
            }
            
            // Check and add ms_per_gram_large column
            try {
                stmt.execute("ALTER TABLE ingredients ADD COLUMN ms_per_gram_large INTEGER");
                log.info("Added column ms_per_gram_large to ingredients table");
            } catch (SQLException e) {
                if (e.getMessage().contains("duplicate column")) {
                    log.debug("Column ms_per_gram_large already exists");
                } else {
                    throw e;
                }
            }
            
            // Check and add ms_per_gram_small column
            try {
                stmt.execute("ALTER TABLE ingredients ADD COLUMN ms_per_gram_small INTEGER");
                log.info("Added column ms_per_gram_small to ingredients table");
            } catch (SQLException e) {
                if (e.getMessage().contains("duplicate column")) {
                    log.debug("Column ms_per_gram_small already exists");
                } else {
                    throw e;
                }
            }
            
            // Check and add pump_threshold_grams column
            try {
                stmt.execute("ALTER TABLE ingredients ADD COLUMN pump_threshold_grams REAL");
                log.info("Added column pump_threshold_grams to ingredients table");
            } catch (SQLException e) {
                if (e.getMessage().contains("duplicate column")) {
                    log.debug("Column pump_threshold_grams already exists");
                } else {
                    throw e;
                }
            }
            
            // Check and add master_ingredient_id column
            try {
                stmt.execute("ALTER TABLE ingredients ADD COLUMN master_ingredient_id INTEGER");
                log.info("Added column master_ingredient_id to ingredients table");
            } catch (SQLException e) {
                if (e.getMessage().contains("duplicate column")) {
                    log.debug("Column master_ingredient_id already exists");
                } else {
                    throw e;
                }
            }
            
            // Remove UNIQUE constraint on cas_number if it exists (SQLite doesn't support DROP CONSTRAINT directly)
            // We need to recreate the table. Check if we need to do this migration.
            try {
                // Try to insert duplicate CAS to see if UNIQUE constraint exists
                stmt.execute("PRAGMA table_info(ingredients)");
            } catch (SQLException e) {
                log.debug("Could not check table info", e);
            }
        } catch (SQLException e) {
            log.error("Error migrating ingredients table", e);
        }
        
        // Remove UNIQUE constraint on cas_number by recreating table if needed
        try (Statement migrateStmt = connection.createStatement()) {
            // Check if we need to migrate (old schema with UNIQUE constraint)
            boolean needsMigration = false;
            try (java.sql.ResultSet rs = migrateStmt.executeQuery(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name='ingredients'")) {
                if (rs.next()) {
                    String createSql = rs.getString("sql");
                    if (createSql != null && createSql.contains("cas_number TEXT UNIQUE")) {
                        needsMigration = true;
                        log.info("Detected UNIQUE constraint on cas_number, will migrate table");
                    }
                }
            }
            
            if (needsMigration) {
                log.info("Migrating ingredients table to remove UNIQUE constraint on cas_number");
                
                // Create new table without UNIQUE
                migrateStmt.execute("""
                    CREATE TABLE ingredients_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        description TEXT,
                        category TEXT,
                        cas_number TEXT,
                        ifra_naturals_category TEXT,
                        ifra_status TEXT,
                        arduino_uid TEXT,
                        arduino_pin INTEGER,
                        arduino_uid_small TEXT,
                        arduino_pin_small INTEGER,
                        default_duration INTEGER,
                        ms_per_gram_large INTEGER,
                        ms_per_gram_small INTEGER,
                        pump_threshold_grams REAL,
                        concentration REAL,
                        unit TEXT,
                        cost_per_unit REAL,
                        stock_quantity REAL,
                        supplier TEXT,
                        batch_number TEXT,
                        master_ingredient_id INTEGER,
                        active INTEGER DEFAULT 1,
                        FOREIGN KEY (master_ingredient_id) REFERENCES ingredients_new(id) ON DELETE SET NULL
                    )
                """);
                
                // Copy data
                migrateStmt.execute("""
                    INSERT INTO ingredients_new 
                    SELECT id, name, description, category, cas_number, ifra_naturals_category, ifra_status,
                           arduino_uid, arduino_pin, arduino_uid_small, arduino_pin_small, default_duration,
                           ms_per_gram_large, ms_per_gram_small, pump_threshold_grams, concentration, unit,
                           cost_per_unit, stock_quantity, supplier, batch_number, NULL as master_ingredient_id, active
                    FROM ingredients
                """);
                
                // Drop old table
                migrateStmt.execute("DROP TABLE ingredients");
                
                // Rename new table
                migrateStmt.execute("ALTER TABLE ingredients_new RENAME TO ingredients");
                
                log.info("Successfully migrated ingredients table - removed UNIQUE constraint on cas_number");
            }
        } catch (SQLException e) {
            log.error("Error removing UNIQUE constraint from cas_number", e);
        }
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.error("Error closing database connection", e);
        }
    }
}


