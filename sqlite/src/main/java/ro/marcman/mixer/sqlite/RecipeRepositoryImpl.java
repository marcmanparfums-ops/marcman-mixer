package ro.marcman.mixer.sqlite;

import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.model.Recipe;
import ro.marcman.mixer.core.model.RecipeIngredient;
import ro.marcman.mixer.core.ports.repository.RecipeRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class RecipeRepositoryImpl implements RecipeRepository {
    
    private final DatabaseManager dbManager;
    private final IngredientRepositoryImpl ingredientRepository;
    
    public RecipeRepositoryImpl(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.ingredientRepository = new IngredientRepositoryImpl(dbManager);
    }
    
    @Override
    public Recipe save(Recipe recipe) {
        if (recipe.getId() == null) {
            return insert(recipe);
        } else {
            return update(recipe);
        }
    }
    
    private Recipe insert(Recipe recipe) {
        String sql = """
            INSERT INTO recipes (name, description, category, created_at, updated_at, 
                               created_by, batch_size, notes, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, recipe.getName());
            pstmt.setString(2, recipe.getDescription());
            pstmt.setString(3, recipe.getCategory());
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.setString(5, LocalDateTime.now().toString());
            pstmt.setString(6, recipe.getCreatedBy());
            pstmt.setObject(7, recipe.getBatchSize());
            pstmt.setString(8, recipe.getNotes());
            pstmt.setInt(9, recipe.isActive() ? 1 : 0);
            
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                recipe.setId(rs.getLong(1));
            }
            
            // Save recipe ingredients
            saveRecipeIngredients(recipe);
            
            log.info("Inserted recipe: {} with {} ingredients", recipe.getName(), recipe.getIngredients().size());
            return recipe;
            
        } catch (SQLException e) {
            log.error("Error inserting recipe", e);
            return null;
        }
    }
    
    private Recipe update(Recipe recipe) {
        String sql = """
            UPDATE recipes 
            SET name = ?, description = ?, category = ?, updated_at = ?,
                created_by = ?, batch_size = ?, notes = ?, active = ?
            WHERE id = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, recipe.getName());
            pstmt.setString(2, recipe.getDescription());
            pstmt.setString(3, recipe.getCategory());
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.setString(5, recipe.getCreatedBy());
            pstmt.setObject(6, recipe.getBatchSize());
            pstmt.setString(7, recipe.getNotes());
            pstmt.setInt(8, recipe.isActive() ? 1 : 0);
            pstmt.setLong(9, recipe.getId());
            
            pstmt.executeUpdate();
            
            // Delete old ingredients and save new ones
            deleteRecipeIngredients(recipe.getId());
            saveRecipeIngredients(recipe);
            
            log.info("Updated recipe: {}", recipe.getName());
            return recipe;
            
        } catch (SQLException e) {
            log.error("Error updating recipe", e);
            return null;
        }
    }
    
    private void saveRecipeIngredients(Recipe recipe) throws SQLException {
        String sql = """
            INSERT INTO recipe_ingredients (recipe_id, ingredient_id, quantity, unit, 
                                          pulse_duration, sequence_order, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (RecipeIngredient ri : recipe.getIngredients()) {
                pstmt.setLong(1, recipe.getId());
                pstmt.setLong(2, ri.getIngredientId());
                pstmt.setObject(3, ri.getQuantity());
                pstmt.setString(4, ri.getUnit());
                pstmt.setObject(5, ri.getPulseDuration());
                pstmt.setObject(6, ri.getSequenceOrder());
                pstmt.setString(7, ri.getNotes());
                pstmt.executeUpdate();
            }
        }
    }
    
    private void deleteRecipeIngredients(Long recipeId) throws SQLException {
        String sql = "DELETE FROM recipe_ingredients WHERE recipe_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, recipeId);
            pstmt.executeUpdate();
        }
    }
    
    @Override
    public Optional<Recipe> findById(Long id) {
        String sql = "SELECT * FROM recipes WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Recipe recipe = mapResultSetToRecipe(rs);
                loadRecipeIngredients(recipe);
                return Optional.of(recipe);
            }
            
        } catch (SQLException e) {
            log.error("Error finding recipe by id", e);
        }
        
        return Optional.empty();
    }
    
    @Override
    public List<Recipe> findAll() {
        List<Recipe> recipes = new ArrayList<>();
        String sql = "SELECT * FROM recipes ORDER BY created_at DESC";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            // First, load all recipes
            while (rs.next()) {
                Recipe recipe = mapResultSetToRecipe(rs);
                recipes.add(recipe);
            }
            
        } catch (SQLException e) {
            log.error("Error finding all recipes", e);
        }
        
        // Then load ingredients for each (separate connections)
        for (Recipe recipe : recipes) {
            loadRecipeIngredients(recipe);
        }
        
        return recipes;
    }
    
    @Override
    public List<Recipe> findAllActive() {
        List<Recipe> recipes = new ArrayList<>();
        String sql = "SELECT * FROM recipes WHERE active = 1 ORDER BY created_at DESC";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            // First, load all recipes
            while (rs.next()) {
                Recipe recipe = mapResultSetToRecipe(rs);
                recipes.add(recipe);
            }
            
        } catch (SQLException e) {
            log.error("Error finding active recipes", e);
        }
        
        // Then load ingredients for each (separate connections)
        for (Recipe recipe : recipes) {
            loadRecipeIngredients(recipe);
        }
        
        return recipes;
    }
    
    @Override
    public void deleteById(Long id) {
        String sql = "DELETE FROM recipes WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
            log.info("Deleted recipe with id: {}", id);
            
        } catch (SQLException e) {
            log.error("Error deleting recipe", e);
        }
    }
    
    @Override
    public List<Recipe> searchByName(String name) {
        List<Recipe> recipes = new ArrayList<>();
        String sql = "SELECT * FROM recipes WHERE name LIKE ? ORDER BY created_at DESC";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "%" + name + "%");
            ResultSet rs = pstmt.executeQuery();
            
            // First, load all recipes
            while (rs.next()) {
                Recipe recipe = mapResultSetToRecipe(rs);
                recipes.add(recipe);
            }
            
        } catch (SQLException e) {
            log.error("Error searching recipes by name", e);
        }
        
        // Then load ingredients for each
        for (Recipe recipe : recipes) {
            loadRecipeIngredients(recipe);
        }
        
        return recipes;
    }
    
    @Override
    public List<Recipe> findByCategory(String category) {
        List<Recipe> recipes = new ArrayList<>();
        String sql = "SELECT * FROM recipes WHERE category = ? ORDER BY created_at DESC";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Recipe recipe = mapResultSetToRecipe(rs);
                loadRecipeIngredients(recipe);
                recipes.add(recipe);
            }
            
        } catch (SQLException e) {
            log.error("Error finding recipes by category", e);
        }
        
        return recipes;
    }
    
    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM recipes WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            log.error("Error checking if recipe exists", e);
        }
        
        return false;
    }
    
    private void loadRecipeIngredients(Recipe recipe) {
        String sql = """
            SELECT id, recipe_id, ingredient_id, quantity, unit, pulse_duration, sequence_order, notes
            FROM recipe_ingredients
            WHERE recipe_id = ?
            ORDER BY sequence_order
            """;
        
        // STEP 1: First, collect all RecipeIngredient data (without nested DB calls)
        List<RecipeIngredient> ingredients = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, recipe.getId());
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Long ingredientId = rs.getLong("ingredient_id");
                
                RecipeIngredient ri = RecipeIngredient.builder()
                    .id(rs.getLong("id"))
                    .recipeId(rs.getLong("recipe_id"))
                    .ingredientId(ingredientId)
                    .quantity(rs.getObject("quantity") != null ? rs.getDouble("quantity") : null)
                    .unit(rs.getString("unit"))
                    .pulseDuration(rs.getObject("pulse_duration") != null ? rs.getInt("pulse_duration") : null)
                    .sequenceOrder(rs.getObject("sequence_order") != null ? rs.getInt("sequence_order") : null)
                    .notes(rs.getString("notes"))
                    .build();
                
                ingredients.add(ri);
            }
            // ResultSet is closed here automatically
            
        } catch (SQLException e) {
            log.error("Error loading recipe ingredients", e);
            e.printStackTrace();
            return;
        }
        
        // STEP 2: Now that ResultSet is closed, load ingredient details
        // Note: ingredientRepository.findById() will automatically apply master configuration if needed
        for (RecipeIngredient ri : ingredients) {
            Ingredient ingredient = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
            ri.setIngredient(ingredient);
        }
        
        recipe.setIngredients(ingredients);
    }
    
    private Recipe mapResultSetToRecipe(ResultSet rs) throws SQLException {
        return Recipe.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .category(rs.getString("category"))
            .createdAt(rs.getString("created_at") != null ? LocalDateTime.parse(rs.getString("created_at")) : null)
            .updatedAt(rs.getString("updated_at") != null ? LocalDateTime.parse(rs.getString("updated_at")) : null)
            .createdBy(rs.getString("created_by"))
            .batchSize(rs.getInt("batch_size"))
            .notes(rs.getString("notes"))
            .active(rs.getInt("active") == 1)
            .build();
    }
}

