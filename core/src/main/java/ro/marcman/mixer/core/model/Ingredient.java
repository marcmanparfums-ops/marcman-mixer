package ro.marcman.mixer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing a perfume ingredient.
 * Each ingredient can be controlled via Arduino pins.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient {
    private Long id;
    private String name;
    private String description;
    private String category;
    
    // IFRA Standard properties (from transparency list)
    private String casNumber;  // CAS number (Chemical Abstracts Service)
    private String ifraNaturalsCategory;  // IFRA Naturals (NCS) category (e.g., K2.12, G2.30)
    private String ifraStatus;  // IFRA status (approved, restricted, etc.)
    
    // Arduino control properties
    private String arduinoUid;  // UID of the slave Arduino for LARGE pump (e.g., "0x1")
    private Integer arduinoPin;  // Pin number for LARGE pump (for quantities >= 10g)
    private String arduinoUidSmall;  // UID of the slave Arduino for SMALL pump (e.g., "0x2")
    private Integer arduinoPinSmall;  // Pin number for SMALL pump (for quantities < 10g)
    private Integer defaultDuration; // Default pulse duration in milliseconds
    
    // Pumping calibration (viscosity-dependent)
    private Integer msPerGramLarge;  // Milliseconds needed to pump 1 gram with LARGE pump (default: 20)
    private Integer msPerGramSmall;  // Milliseconds needed to pump 1 gram with SMALL pump (default: 20)
    private Double pumpThresholdGrams;  // Threshold in grams: < threshold → SMALL pump, >= threshold → LARGE pump (default: 10.0)
    
    // Physical properties
    private Double concentration;  // Concentration percentage
    private String unit;  // Unit of measurement (ml, drops, etc.)
    private Double costPerUnit;  // Cost per unit
    private Double stockQuantity;  // Current stock quantity
    
    // Supplier information
    private String supplier;
    private String batchNumber;
    
    // Ingredient association (for same CAS number, different names from different suppliers)
    private Long masterIngredientId;  // If set, this ingredient uses Arduino config from master ingredient
    
    private boolean active;
}


