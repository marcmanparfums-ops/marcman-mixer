package ro.marcman.mixer.core.ports.repository;

import ro.marcman.mixer.core.model.Recipe;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Recipe persistence.
 */
public interface RecipeRepository {
    
    /**
     * Save or update a recipe
     */
    Recipe save(Recipe recipe);
    
    /**
     * Find recipe by ID
     */
    Optional<Recipe> findById(Long id);
    
    /**
     * Find all recipes
     */
    List<Recipe> findAll();
    
    /**
     * Find active recipes only
     */
    List<Recipe> findAllActive();
    
    /**
     * Delete recipe by ID
     */
    void deleteById(Long id);
    
    /**
     * Search recipes by name
     */
    List<Recipe> searchByName(String name);
    
    /**
     * Find recipes by category
     */
    List<Recipe> findByCategory(String category);
    
    /**
     * Check if recipe exists by ID
     */
    boolean existsById(Long id);
}
