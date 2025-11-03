package ro.marcman.mixer.core.ports.repository;

import ro.marcman.mixer.core.model.RecipeIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for RecipeIngredient persistence.
 */
public interface RecipeIngredientRepository {
    List<RecipeIngredient> findAll();
    Optional<RecipeIngredient> findById(Long id);
    List<RecipeIngredient> findByRecipeId(Long recipeId);
    List<RecipeIngredient> findByIngredientId(Long ingredientId);
    RecipeIngredient save(RecipeIngredient recipeIngredient);
    void deleteById(Long id);
    void deleteByRecipeId(Long recipeId);
    boolean existsById(Long id);
}


