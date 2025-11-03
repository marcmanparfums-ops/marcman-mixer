package ro.marcman.mixer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a perfume recipe.
 * A recipe contains multiple ingredients with specific durations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {
    private Long id;
    private String name;
    private String description;
    private String category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private Integer batchSize;
    private String notes;
    private boolean active;
    
    // Recipe ingredients with their specific durations
    @Builder.Default
    private List<RecipeIngredient> ingredients = new ArrayList<>();
    
    /**
     * Calculate total duration for the recipe
     */
    public int getTotalDuration() {
        return ingredients.stream()
            .filter(ri -> ri.getPulseDuration() != null)
            .mapToInt(RecipeIngredient::getPulseDuration)
            .sum();
    }
    
    /**
     * Get number of ingredients in recipe
     */
    public int getIngredientCount() {
        return ingredients.size();
    }
}
