package ro.marcman.mixer.serial;

import ro.marcman.mixer.serial.model.SerialResponse;

/**
 * Listener interface for serial communication events.
 */
public interface SerialListener {
    
    /**
     * Called when data is received from the Arduino.
     */
    void onDataReceived(SerialResponse response);
    
    /**
     * Called when an error occurs during serial communication.
     */
    void onError(String error);
    
    /**
     * Called when the serial connection is established.
     */
    void onConnected(String portName);
    
    /**
     * Called when the serial connection is closed.
     */
    void onDisconnected();
}


