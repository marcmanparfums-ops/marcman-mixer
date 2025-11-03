package ro.marcman.mixer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an ingredient within a recipe with its specific duration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredient {
    private Long id;
    private Long recipeId;
    private Long ingredientId;
    private Double quantity;  // Quantity in recipe
    private String unit;  // Unit (ml, drops, etc.)
    private Integer pulseDuration;  // Duration in milliseconds for THIS recipe
    private Integer sequenceOrder;  // Order in which to pump (0, 1, 2, ...)
    private String notes;  // Notes for this ingredient in recipe
    
    // Transient - populated when loading from DB
    private Ingredient ingredient;  // Full ingredient details
    
    /**
     * Get display name
     */
    public String getDisplayName() {
        if (ingredient != null) {
            return ingredient.getName();
        }
        return "Ingredient #" + ingredientId;
    }
    
    /**
     * Get SLAVE UID for pumping
     */
    public String getSlaveUid() {
        if (ingredient != null) {
            return ingredient.getArduinoUid();
        }
        return null;
    }
    
    /**
     * Get Arduino PIN for pumping
     */
    public Integer getArduinoPin() {
        if (ingredient != null) {
            return ingredient.getArduinoPin();
        }
        return null;
    }
    
    /**
     * Get duration for display
     */
    public String getDurationDisplay() {
        if (pulseDuration != null) {
            return pulseDuration + " ms";
        }
        return "Not set";
    }
    
    /**
     * Get quantity for display
     */
    public String getQuantityDisplay() {
        if (quantity != null) {
            return String.format("%.2f %s", quantity, unit != null ? unit : "");
        }
        return "N/A";
    }
}
