package ro.marcman.mixer.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.Recipe;
import ro.marcman.mixer.core.ports.repository.RecipeRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service class for managing recipes.
 */
@Slf4j
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository repository;

    public List<Recipe> findAll() {
        log.debug("Finding all recipes");
        return repository.findAll();
    }

    public Optional<Recipe> findById(Long id) {
        log.debug("Finding recipe by id: {}", id);
        return repository.findById(id);
    }

    public List<Recipe> findByCategory(String category) {
        log.debug("Finding recipes by category: {}", category);
        return repository.findByCategory(category);
    }

    public Recipe save(Recipe recipe) {
        if (recipe.getId() == null) {
            recipe.setCreatedAt(LocalDateTime.now());
        }
        recipe.setUpdatedAt(LocalDateTime.now());
        
        log.info("Saving recipe: {}", recipe.getName());
        return repository.save(recipe);
    }

    public void deleteById(Long id) {
        log.info("Deleting recipe with id: {}", id);
        repository.deleteById(id);
    }

    public boolean existsById(Long id) {
        return repository.existsById(id);
    }
}


