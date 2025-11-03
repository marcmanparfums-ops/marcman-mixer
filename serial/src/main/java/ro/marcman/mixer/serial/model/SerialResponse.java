package ro.marcman.mixer.serial.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a response received from the Arduino MASTER.
 */
@Data
@Builder
public class SerialResponse {
    private String rawResponse;
    private LocalDateTime timestamp;
    private ResponseType type;
    private boolean error;
    
    public enum ResponseType {
        ACK,            // Command acknowledged
        DATA,           // Data response
        ERROR,          // Error message
        LOG,            // Log entry
        TABLE,          // Table data
        UNKNOWN         // Unknown response
    }
    
    /**
     * Parse response type from raw text
     */
    public static ResponseType parseType(String response) {
        if (response == null || response.trim().isEmpty()) {
            return ResponseType.UNKNOWN;
        }
        
        String lower = response.toLowerCase();
        
        // Skip CAN recovery messages - these are NORMAL, not errors!
        if (lower.contains("eflg") || 
            lower.contains("can recover") ||
            (lower.contains("tec=") && lower.contains("rec="))) {
            return ResponseType.DATA;  // Treat as normal data, not error
        }
        
        if (lower.contains("error") || lower.contains("fail") || lower.contains("invalid")) {
            return ResponseType.ERROR;
        }
        if (lower.contains("ok") || lower.contains("success") || lower.contains("ack")) {
            return ResponseType.ACK;
        }
        if (lower.contains("log raw:") || lower.contains("event")) {
            return ResponseType.LOG;
        }
        if (lower.contains("id") && lower.contains("uid") && lower.contains("|")) {
            return ResponseType.TABLE;
        }
        
        return ResponseType.DATA;
    }
    
    public static SerialResponse fromRaw(String raw) {
        ResponseType type = parseType(raw);
        return SerialResponse.builder()
                .rawResponse(raw)
                .timestamp(LocalDateTime.now())
                .type(type)
                .error(type == ResponseType.ERROR)
                .build();
    }
}


