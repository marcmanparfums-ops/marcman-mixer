package ro.marcman.mixer.core.ports.repository;

import ro.marcman.mixer.core.model.Ingredient;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Ingredient persistence.
 */
public interface IngredientRepository {
    List<Ingredient> findAll();
    Optional<Ingredient> findById(Long id);
    List<Ingredient> findByCategory(String category);
    List<Ingredient> findByArduinoUid(String arduinoUid);
    Ingredient save(Ingredient ingredient);
    void deleteById(Long id);
    boolean existsById(Long id);
    
    /**
     * Find all ingredients without applying master configuration.
     * Used internally for counting pins to avoid double-counting.
     */
    List<Ingredient> findAllWithoutMasterApply();
}


