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
     * Find the correct database path.
     * Database is stored in user's Local AppData directory: %LOCALAPPDATA%\MarcmanMixer\marcman_mixer.db
     */
    private static String findDatabasePath() {
        // Get Local AppData directory (Windows: C:\Users\Username\AppData\Local)
        String localAppData = System.getenv("LOCALAPPDATA");
        
        // Fallback for non-Windows or if LOCALAPPDATA is not set
        if (localAppData == null || localAppData.isEmpty()) {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                // Try to construct the path manually
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    // Windows: user.home\AppData\Local
                    localAppData = userHome + File.separator + "AppData" + File.separator + "Local";
                } else {
                    // Unix-like: user.home/.local/share or user.home/.config
                    localAppData = userHome + File.separator + ".local" + File.separator + "share";
                }
            } else {
                // Last resort: use current directory
                localAppData = System.getProperty("user.dir");
            }
        }
        
        // Create MarcmanMixer directory path
        File dbDir = new File(localAppData, "MarcmanMixer");
        File dbFile = new File(dbDir, "marcman_mixer.db");
        
        log.info("Database path: {}", dbFile.getAbsolutePath());
        return dbFile.getAbsolutePath();
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
     * Get the database file path
     * @return Absolute path to the database file
     */
    public String getDatabasePath() {
        return DB_PATH;
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


