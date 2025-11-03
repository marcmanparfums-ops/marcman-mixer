package ro.marcman.mixer.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.RecipeIngredient;
import ro.marcman.mixer.core.ports.repository.RecipeIngredientRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service class for managing recipe ingredients.
 */
@Slf4j
@RequiredArgsConstructor
public class RecipeIngredientService {
    private final RecipeIngredientRepository repository;

    public List<RecipeIngredient> findAll() {
        log.debug("Finding all recipe ingredients");
        return repository.findAll();
    }

    public Optional<RecipeIngredient> findById(Long id) {
        log.debug("Finding recipe ingredient by id: {}", id);
        return repository.findById(id);
    }

    public List<RecipeIngredient> findByRecipeId(Long recipeId) {
        log.debug("Finding recipe ingredients by recipe id: {}", recipeId);
        return repository.findByRecipeId(recipeId);
    }

    public List<RecipeIngredient> findByIngredientId(Long ingredientId) {
        log.debug("Finding recipe ingredients by ingredient id: {}", ingredientId);
        return repository.findByIngredientId(ingredientId);
    }

    public RecipeIngredient save(RecipeIngredient recipeIngredient) {
        log.info("Saving recipe ingredient");
        return repository.save(recipeIngredient);
    }

    public void deleteById(Long id) {
        log.info("Deleting recipe ingredient with id: {}", id);
        repository.deleteById(id);
    }

    public void deleteByRecipeId(Long recipeId) {
        log.info("Deleting all ingredients for recipe id: {}", recipeId);
        repository.deleteByRecipeId(recipeId);
    }

    public boolean existsById(Long id) {
        return repository.existsById(id);
    }
}


