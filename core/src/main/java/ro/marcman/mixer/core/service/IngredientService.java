package ro.marcman.mixer.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.ports.repository.IngredientRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service class for managing ingredients.
 */
@Slf4j
@RequiredArgsConstructor
public class IngredientService {
    private final IngredientRepository repository;

    public List<Ingredient> findAll() {
        log.debug("Finding all ingredients");
        return repository.findAll();
    }

    public Optional<Ingredient> findById(Long id) {
        log.debug("Finding ingredient by id: {}", id);
        return repository.findById(id);
    }

    public List<Ingredient> findByCategory(String category) {
        log.debug("Finding ingredients by category: {}", category);
        return repository.findByCategory(category);
    }

    public List<Ingredient> findByArduinoUid(String arduinoUid) {
        log.debug("Finding ingredients by Arduino UID: {}", arduinoUid);
        return repository.findByArduinoUid(arduinoUid);
    }

    public Ingredient save(Ingredient ingredient) {
        log.info("Saving ingredient: {}", ingredient.getName());
        return repository.save(ingredient);
    }

    public void deleteById(Long id) {
        log.info("Deleting ingredient with id: {}", id);
        repository.deleteById(id);
    }

    public boolean existsById(Long id) {
        return repository.existsById(id);
    }
}


