package ro.marcman.mixer.sqlite;

import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.ports.repository.IngredientRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of IngredientRepository.
 */
@Slf4j
public class IngredientRepositoryImpl implements IngredientRepository {
    
    private final DatabaseManager dbManager;
    
    public IngredientRepositoryImpl(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    @Override
    public List<Ingredient> findAll() {
        List<Ingredient> ingredients = new ArrayList<>();
        String sql = "SELECT * FROM ingredients ORDER BY name";  // Removed active filter
        
        // First, load all ingredients without master configuration
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Ingredient ingredient = mapResultSetToIngredient(rs);
                ingredients.add(ingredient);
            }
        } catch (SQLException e) {
            log.error("Error finding all ingredients", e);
        }
        
        // Now, apply master configuration after ResultSet is closed
        for (Ingredient ingredient : ingredients) {
            applyMasterConfiguration(ingredient);
        }
        
        return ingredients;
    }
    
    @Override
    public Optional<Ingredient> findById(Long id) {
        // Guard: id cannot be null
        if (id == null) {
            log.warn("findById called with null id");
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM ingredients WHERE id = ?";
        Ingredient ingredient = null;
        
        // First, load ingredient without master configuration
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                ingredient = mapResultSetToIngredient(rs);
            }
            rs.close(); // Close ResultSet before applying master config
        } catch (SQLException e) {
            log.error("Error finding ingredient by id: {}", id, e);
            return Optional.empty();
        }
        
        // Now apply master configuration after ResultSet is closed
        if (ingredient != null) {
            applyMasterConfiguration(ingredient);
            return Optional.of(ingredient);
        }
        
        return Optional.empty();
    }
    
    @Override
    public List<Ingredient> findByCategory(String category) {
        List<Ingredient> ingredients = new ArrayList<>();
        String sql = "SELECT * FROM ingredients WHERE category = ? AND active = 1 ORDER BY name";
        
        // First, load all ingredients without master configuration
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Ingredient ingredient = mapResultSetToIngredient(rs);
                ingredients.add(ingredient);
            }
        } catch (SQLException e) {
            log.error("Error finding ingredients by category: {}", category, e);
        }
        
        // Now, apply master configuration after ResultSet is closed
        for (Ingredient ingredient : ingredients) {
            applyMasterConfiguration(ingredient);
        }
        
        return ingredients;
    }
    
    @Override
    public List<Ingredient> findByArduinoUid(String arduinoUid) {
        List<Ingredient> ingredients = new ArrayList<>();
        String sql = "SELECT * FROM ingredients WHERE arduino_uid = ? AND active = 1 ORDER BY arduino_pin";
        
        // First, load all ingredients without master configuration
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, arduinoUid);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Ingredient ingredient = mapResultSetToIngredient(rs);
                ingredients.add(ingredient);
            }
        } catch (SQLException e) {
            log.error("Error finding ingredients by Arduino UID: {}", arduinoUid, e);
        }
        
        // Now, apply master configuration after ResultSet is closed
        for (Ingredient ingredient : ingredients) {
            applyMasterConfiguration(ingredient);
        }
        
        return ingredients;
    }
    
    @Override
    public Ingredient save(Ingredient ingredient) {
        if (ingredient.getId() == null) {
            return insert(ingredient);
        } else {
            return update(ingredient);
        }
    }
    
    private Ingredient insert(Ingredient ingredient) {
        String sql = """
            INSERT INTO ingredients (name, description, category, cas_number, ifra_naturals_category, 
                                     ifra_status, arduino_uid, arduino_pin, arduino_uid_small, arduino_pin_small, 
                                     default_duration, ms_per_gram_large, ms_per_gram_small, pump_threshold_grams,
                                     concentration, unit, cost_per_unit, stock_quantity, 
                                     supplier, batch_number, master_ingredient_id, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setIngredientParameters(pstmt, ingredient);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                ingredient.setId(rs.getLong(1));
                log.info("Inserted ingredient: {} with id: {}", ingredient.getName(), ingredient.getId());
            } else {
                log.error("FAILED to get generated key after inserting ingredient: {}", ingredient.getName());
                throw new RuntimeException("Failed to get generated ID after insert");
            }
            
            return ingredient;
            
        } catch (SQLException e) {
            log.error("Error inserting ingredient: {} - SQL Error Code: {}, SQL State: {}, Message: {}", 
                     ingredient.getName(), e.getErrorCode(), e.getSQLState(), e.getMessage(), e);
            log.error("Ingredient details: name={}, cas={}, arduinoUid={}, arduinoPin={}, arduinoUidSmall={}, arduinoPinSmall={}", 
                     ingredient.getName(), ingredient.getCasNumber(), ingredient.getArduinoUid(), 
                     ingredient.getArduinoPin(), ingredient.getArduinoUidSmall(), ingredient.getArduinoPinSmall());
            throw new RuntimeException("Failed to insert ingredient: " + e.getMessage() + 
                                      (e.getSQLState() != null ? " (SQL State: " + e.getSQLState() + ")" : ""), e);
        }
    }
    
    private Ingredient update(Ingredient ingredient) {
        String sql = """
            UPDATE ingredients 
            SET name = ?, description = ?, category = ?, cas_number = ?, ifra_naturals_category = ?,
                ifra_status = ?, arduino_uid = ?, arduino_pin = ?, arduino_uid_small = ?, arduino_pin_small = ?, 
                default_duration = ?, ms_per_gram_large = ?, ms_per_gram_small = ?, pump_threshold_grams = ?,
                concentration = ?, unit = ?, cost_per_unit = ?, stock_quantity = ?,
                supplier = ?, batch_number = ?, master_ingredient_id = ?, active = ?
            WHERE id = ?
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            setIngredientParameters(pstmt, ingredient);
            pstmt.setLong(23, ingredient.getId());
            pstmt.executeUpdate();
            
            log.info("Updated ingredient: {}", ingredient.getName());
            return ingredient;
            
        } catch (SQLException e) {
            log.error("Error updating ingredient: {} - SQL Error Code: {}, SQL State: {}, Message: {}", 
                     ingredient.getName(), e.getErrorCode(), e.getSQLState(), e.getMessage(), e);
            throw new RuntimeException("Failed to update ingredient", e);
        }
    }
    
    @Override
    public void deleteById(Long id) {
        // Soft delete
        String sql = "UPDATE ingredients SET active = 0 WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
            log.info("Deleted ingredient with id: {}", id);
            
        } catch (SQLException e) {
            log.error("Error deleting ingredient with id: {}", id, e);
        }
    }
    
    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM ingredients WHERE id = ? AND active = 1";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error("Error checking if ingredient exists with id: {}", id, e);
        }
        
        return false;
    }
    
    private void setIngredientParameters(PreparedStatement pstmt, Ingredient ingredient) throws SQLException {
        pstmt.setString(1, ingredient.getName());
        pstmt.setString(2, ingredient.getDescription());
        pstmt.setString(3, ingredient.getCategory());
        pstmt.setString(4, ingredient.getCasNumber());
        pstmt.setString(5, ingredient.getIfraNaturalsCategory());
        pstmt.setString(6, ingredient.getIfraStatus());
        pstmt.setString(7, ingredient.getArduinoUid());
        
        if (ingredient.getArduinoPin() != null) {
            pstmt.setInt(8, ingredient.getArduinoPin());
        } else {
            pstmt.setNull(8, Types.INTEGER);
        }
        
        pstmt.setString(9, ingredient.getArduinoUidSmall());
        
        if (ingredient.getArduinoPinSmall() != null) {
            pstmt.setInt(10, ingredient.getArduinoPinSmall());
        } else {
            pstmt.setNull(10, Types.INTEGER);
        }
        
        if (ingredient.getDefaultDuration() != null) {
            pstmt.setInt(11, ingredient.getDefaultDuration());
        } else {
            pstmt.setNull(11, Types.INTEGER);
        }
        
        if (ingredient.getMsPerGramLarge() != null) {
            pstmt.setInt(12, ingredient.getMsPerGramLarge());
        } else {
            pstmt.setNull(12, Types.INTEGER);
        }
        
        if (ingredient.getMsPerGramSmall() != null) {
            pstmt.setInt(13, ingredient.getMsPerGramSmall());
        } else {
            pstmt.setNull(13, Types.INTEGER);
        }
        
        if (ingredient.getPumpThresholdGrams() != null) {
            pstmt.setDouble(14, ingredient.getPumpThresholdGrams());
        } else {
            pstmt.setNull(14, Types.DOUBLE);
        }
        
        if (ingredient.getConcentration() != null) {
            pstmt.setDouble(15, ingredient.getConcentration());
        } else {
            pstmt.setNull(15, Types.DOUBLE);
        }
        
        pstmt.setString(16, ingredient.getUnit());
        
        if (ingredient.getCostPerUnit() != null) {
            pstmt.setDouble(17, ingredient.getCostPerUnit());
        } else {
            pstmt.setNull(17, Types.DOUBLE);
        }
        
        if (ingredient.getStockQuantity() != null) {
            pstmt.setDouble(18, ingredient.getStockQuantity());
        } else {
            pstmt.setNull(18, Types.DOUBLE);
        }
        
        pstmt.setString(19, ingredient.getSupplier());
        pstmt.setString(20, ingredient.getBatchNumber());
        
        if (ingredient.getMasterIngredientId() != null) {
            pstmt.setLong(21, ingredient.getMasterIngredientId());
        } else {
            pstmt.setNull(21, Types.INTEGER);
        }
        
        pstmt.setInt(22, ingredient.isActive() ? 1 : 0);
    }
    
    private Ingredient mapResultSetToIngredient(ResultSet rs) throws SQLException {
        // Safe integer reading - handle NULL and invalid values
        Integer arduinoPin = null;
        try {
            int pinValue = rs.getInt("arduino_pin");
            if (!rs.wasNull()) arduinoPin = pinValue;
        } catch (Exception e) { /* ignore */ }
        
        Integer arduinoPinSmall = null;
        try {
            int pinSmallValue = rs.getInt("arduino_pin_small");
            if (!rs.wasNull()) arduinoPinSmall = pinSmallValue;
        } catch (Exception e) { /* ignore */ }
        
        Integer defaultDuration = null;
        try {
            int durValue = rs.getInt("default_duration");
            if (!rs.wasNull()) defaultDuration = durValue;
        } catch (Exception e) { /* ignore */ }
        
        Double concentration = null;
        try {
            double concValue = rs.getDouble("concentration");
            if (!rs.wasNull()) concentration = concValue;
        } catch (Exception e) { /* ignore */ }
        
        Double costPerUnit = null;
        try {
            double costValue = rs.getDouble("cost_per_unit");
            if (!rs.wasNull()) costPerUnit = costValue;
        } catch (Exception e) { /* ignore */ }
        
        Double stockQuantity = null;
        try {
            double stockValue = rs.getDouble("stock_quantity");
            if (!rs.wasNull()) stockQuantity = stockValue;
        } catch (Exception e) { /* ignore */ }
        
        Integer msPerGramLarge = null;
        try {
            int msLargeValue = rs.getInt("ms_per_gram_large");
            if (!rs.wasNull()) msPerGramLarge = msLargeValue;
        } catch (Exception e) { /* ignore - column might not exist */ }
        
        Integer msPerGramSmall = null;
        try {
            int msSmallValue = rs.getInt("ms_per_gram_small");
            if (!rs.wasNull()) msPerGramSmall = msSmallValue;
        } catch (Exception e) { /* ignore - column might not exist */ }
        
        Double pumpThresholdGrams = null;
        try {
            double thresholdValue = rs.getDouble("pump_threshold_grams");
            if (!rs.wasNull()) pumpThresholdGrams = thresholdValue;
        } catch (Exception e) { /* ignore - column might not exist */ }
        
        return Ingredient.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .category(rs.getString("category"))
                .casNumber(rs.getString("cas_number"))
                .ifraNaturalsCategory(rs.getString("ifra_naturals_category"))
                .ifraStatus(rs.getString("ifra_status"))
                .arduinoUid(rs.getString("arduino_uid"))
                .arduinoPin(arduinoPin)
                .arduinoUidSmall(rs.getString("arduino_uid_small"))
                .arduinoPinSmall(arduinoPinSmall)
                .defaultDuration(defaultDuration)
                .msPerGramLarge(msPerGramLarge)
                .msPerGramSmall(msPerGramSmall)
                .pumpThresholdGrams(pumpThresholdGrams)
                .concentration(concentration)
                .unit(rs.getString("unit"))
                .costPerUnit(costPerUnit)
                .stockQuantity(stockQuantity)
                .supplier(rs.getString("supplier"))
                .batchNumber(rs.getString("batch_number"))
                .masterIngredientId(getLongOrNull(rs, "master_ingredient_id"))
                .active(rs.getInt("active") == 1)
                .build();
    }
    
    private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        try {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Apply master ingredient's Arduino configuration to an ingredient
     */
    private void applyMasterConfiguration(Ingredient ingredient) {
        if (ingredient != null && ingredient.getMasterIngredientId() != null) {
            log.info("Applying master config for ingredient: {} (masterId: {})", 
                     ingredient.getName(), ingredient.getMasterIngredientId());
            // Load master without applying its master config (avoid recursion)
            Ingredient masterIngredient = findByIdWithoutMasterApply(ingredient.getMasterIngredientId());
            if (masterIngredient != null) {
                log.info("Master found: {}, Arduino config: uid={}, pin={}, uidSmall={}, pinSmall={}", 
                         masterIngredient.getName(), 
                         masterIngredient.getArduinoUid(), masterIngredient.getArduinoPin(),
                         masterIngredient.getArduinoUidSmall(), masterIngredient.getArduinoPinSmall());
                
                // Copy Arduino configuration from master
                ingredient.setArduinoUid(masterIngredient.getArduinoUid());
                ingredient.setArduinoPin(masterIngredient.getArduinoPin());
                ingredient.setArduinoUidSmall(masterIngredient.getArduinoUidSmall());
                ingredient.setArduinoPinSmall(masterIngredient.getArduinoPinSmall());
                ingredient.setDefaultDuration(masterIngredient.getDefaultDuration());
                ingredient.setMsPerGramLarge(masterIngredient.getMsPerGramLarge());
                ingredient.setMsPerGramSmall(masterIngredient.getMsPerGramSmall());
                ingredient.setPumpThresholdGrams(masterIngredient.getPumpThresholdGrams());
                
                log.info("After applying master config: uid={}, pin={}, uidSmall={}, pinSmall={}", 
                         ingredient.getArduinoUid(), ingredient.getArduinoPin(),
                         ingredient.getArduinoUidSmall(), ingredient.getArduinoPinSmall());
            } else {
                log.warn("Master ingredient not found for ID: {}", ingredient.getMasterIngredientId());
            }
        }
    }
    
    /**
     * Find ingredient by ID without applying master configuration (used internally to avoid recursion)
     */
    private Ingredient findByIdWithoutMasterApply(Long id) {
        String sql = "SELECT * FROM ingredients WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToIngredient(rs);
            }
        } catch (SQLException e) {
            log.error("Error finding ingredient by id: {}", id, e);
        }
        
        return null;
    }
    
    /**
     * Find all ingredients without applying master configuration.
     * Used internally for counting pins to avoid double-counting.
     */
    public List<Ingredient> findAllWithoutMasterApply() {
        List<Ingredient> ingredients = new ArrayList<>();
        String sql = "SELECT * FROM ingredients ORDER BY name";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ingredients.add(mapResultSetToIngredient(rs));
            }
        } catch (SQLException e) {
            log.error("Error finding all ingredients without master", e);
        }
        
        return ingredients;
    }
}


