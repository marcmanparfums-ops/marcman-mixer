package ro.marcman.mixer.serial.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a command to be sent to the Arduino MASTER.
 */
@Data
@Builder
public class ArduinoCommand {
    private CommandType type;
    private String rawCommand;
    
    public enum CommandType {
        // General commands
        HELP,
        VERSION,
        REANNOUNCE,
        DISCOVER,
        SCAN,
        PING,
        
        // Control commands (ID-based)
        SET,
        SET_PWM,
        PULSE,
        PULSE_GROUP,
        PULSE_PARALLEL,
        
        // Control commands (UID-based) - RECOMMENDED
        PING_UID,
        SET_UID,
        SET_PWM_UID,
        PULSE_UID,
        PULSE_GROUP_UID,
        PULSE_GROUP_UID_LOG,
        PULSE_GROUP_UID_LOGVIEW,
        PULSE_PARALLEL_UID,
        PULSE_PARALLEL_UID_LOG,
        PULSE_PARALLEL_UID_LOGVIEW,
        
        // Monitoring
        TOTAL,
        TOTAL_CLEAR,
        ACTIVE,
        LOG,
        
        // Debug
        DEBUG,
        ERRORS,
        RECOVER,
        
        // Table & Summary
        TABLE,
        SUMMARY,
        CLEAR,
        
        // EEPROM
        EEMAP,
        EECLEAR,
        EESAVE,
        MAPSET,
        MAPDEL,
        MAPSHOW,
        MAPLIST,
        MAPEXPORT,
        MAPIMPORT,
        
        // Test
        TESTALL,
        BATCHTEST,
        
        // Custom
        CUSTOM
    }
    
    /**
     * Helper methods to create common commands
     */
    public static ArduinoCommand help() {
        return ArduinoCommand.builder()
                .type(CommandType.HELP)
                .rawCommand("help")
                .build();
    }
    
    public static ArduinoCommand discover() {
        return ArduinoCommand.builder()
                .type(CommandType.DISCOVER)
                .rawCommand("discover")
                .build();
    }
    
    public static ArduinoCommand scan() {
        return ArduinoCommand.builder()
                .type(CommandType.SCAN)
                .rawCommand("scan")
                .build();
    }
    
    public static ArduinoCommand pingUid(String uid) {
        return ArduinoCommand.builder()
                .type(CommandType.PING_UID)
                .rawCommand("ping_uid " + uid)
                .build();
    }
    
    public static ArduinoCommand setUid(String uid, int pin, boolean value) {
        return ArduinoCommand.builder()
                .type(CommandType.SET_UID)
                .rawCommand("set_uid " + uid + " " + pin + " " + (value ? "1" : "0"))
                .build();
    }
    
    public static ArduinoCommand setPwmUid(String uid, int pin, int duty) {
        return ArduinoCommand.builder()
                .type(CommandType.SET_PWM_UID)
                .rawCommand("setpwm_uid " + uid + " " + pin + " " + duty)
                .build();
    }
    
    public static ArduinoCommand pulseUid(String uid, int pin, int durationMs) {
        return ArduinoCommand.builder()
                .type(CommandType.PULSE_UID)
                .rawCommand("pulse_uid " + uid + " " + pin + " " + durationMs)
                .build();
    }
    
    /**
     * Creates a pulse group command with multiple pins on the same slave.
     * Example: pulsegrp_uid_log 0x12345678 13:500 14:1000 15:750
     */
    public static ArduinoCommand pulseGroupUidLog(String uid, String... pinDurations) {
        StringBuilder cmd = new StringBuilder("pulsegrp_uid_log " + uid);
        for (String pinDuration : pinDurations) {
            cmd.append(" ").append(pinDuration);
        }
        return ArduinoCommand.builder()
                .type(CommandType.PULSE_GROUP_UID_LOG)
                .rawCommand(cmd.toString())
                .build();
    }
    
    /**
     * Creates a parallel pulse command for multiple slaves.
     * Example: pulsepar_uid_log 0x12345678:13:500 0x87654321:14:1000
     */
    public static ArduinoCommand pulseParallelUidLog(String... uidPinDurations) {
        StringBuilder cmd = new StringBuilder("pulsepar_uid_log");
        for (String uidPinDuration : uidPinDurations) {
            cmd.append(" ").append(uidPinDuration);
        }
        return ArduinoCommand.builder()
                .type(CommandType.PULSE_PARALLEL_UID_LOG)
                .rawCommand(cmd.toString())
                .build();
    }
    
    public static ArduinoCommand table() {
        return ArduinoCommand.builder()
                .type(CommandType.TABLE)
                .rawCommand("table")
                .build();
    }
    
    public static ArduinoCommand summary() {
        return ArduinoCommand.builder()
                .type(CommandType.SUMMARY)
                .rawCommand("summary")
                .build();
    }
    
    public static ArduinoCommand custom(String command) {
        return ArduinoCommand.builder()
                .type(CommandType.CUSTOM)
                .rawCommand(command)
                .build();
    }
}


