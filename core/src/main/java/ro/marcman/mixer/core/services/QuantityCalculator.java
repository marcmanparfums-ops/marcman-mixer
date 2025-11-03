package ro.marcman.mixer.core.services;

/**
 * Utility class for calculating ingredient quantities and pump durations
 * based on percentages and batch sizes.
 * 
 * Conversion formula: 1g = 20ms pumping time
 */
public class QuantityCalculator {
    
    /**
     * Conversion factor: 1 gram = 20 milliseconds of pumping
     */
    public static final int MS_PER_GRAM = 20;
    
    /**
     * Calculate absolute quantity in grams from percentage and batch size
     * 
     * @param percentage Percentage of ingredient in recipe (0-100)
     * @param batchSizeGrams Total batch size in grams
     * @return Absolute quantity in grams
     */
    public static double calculateGramsFromPercentage(double percentage, double batchSizeGrams) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }
        if (batchSizeGrams <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        return (percentage / 100.0) * batchSizeGrams;
    }
    
    /**
     * Calculate pumping duration in milliseconds from grams
     * 
     * @param grams Quantity in grams
     * @return Pumping duration in milliseconds
     */
    public static int calculateDurationFromGrams(double grams) {
        if (grams < 0) {
            throw new IllegalArgumentException("Grams must be non-negative");
        }
        return (int) Math.round(grams * MS_PER_GRAM);
    }
    
    /**
     * Calculate pumping duration directly from percentage and batch size
     * 
     * @param percentage Percentage of ingredient in recipe (0-100)
     * @param batchSizeGrams Total batch size in grams
     * @return Pumping duration in milliseconds
     */
    public static int calculateDurationFromPercentage(double percentage, double batchSizeGrams) {
        double grams = calculateGramsFromPercentage(percentage, batchSizeGrams);
        return calculateDurationFromGrams(grams);
    }
    
    /**
     * Calculate percentage from grams and batch size
     * 
     * @param grams Quantity in grams
     * @param batchSizeGrams Total batch size in grams
     * @return Percentage (0-100)
     */
    public static double calculatePercentageFromGrams(double grams, double batchSizeGrams) {
        if (batchSizeGrams <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        return (grams / batchSizeGrams) * 100.0;
    }
    
    /**
     * Calculate grams from pumping duration
     * 
     * @param durationMs Pumping duration in milliseconds
     * @return Quantity in grams
     */
    public static double calculateGramsFromDuration(int durationMs) {
        if (durationMs < 0) {
            throw new IllegalArgumentException("Duration must be non-negative");
        }
        return durationMs / (double) MS_PER_GRAM;
    }
    
    /**
     * Calculate percentage from pumping duration and batch size
     * 
     * @param durationMs Pumping duration in milliseconds
     * @param batchSizeGrams Total batch size in grams
     * @return Percentage (0-100)
     */
    public static double calculatePercentageFromDuration(int durationMs, double batchSizeGrams) {
        double grams = calculateGramsFromDuration(durationMs);
        return calculatePercentageFromGrams(grams, batchSizeGrams);
    }
    
    /**
     * Format quantity display with unit
     * 
     * @param grams Quantity in grams
     * @return Formatted string (e.g., "25.5g")
     */
    public static String formatGrams(double grams) {
        return String.format("%.2fg", grams);
    }
    
    /**
     * Format duration display
     * 
     * @param durationMs Duration in milliseconds
     * @return Formatted string (e.g., "500ms" or "1.5s")
     */
    public static String formatDuration(int durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else {
            return String.format("%.1fs (%dms)", durationMs / 1000.0, durationMs);
        }
    }
    
    /**
     * Format percentage display
     * 
     * @param percentage Percentage value
     * @return Formatted string (e.g., "25.5%")
     */
    public static String formatPercentage(double percentage) {
        return String.format("%.2f%%", percentage);
    }
}


