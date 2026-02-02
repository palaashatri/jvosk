package atri.palaash.jvosk.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;

/**
 * Utilities for checking network connectivity.
 */
public class NetworkUtils {
    
    private static final long CACHE_TIME = 5000; // 5 seconds
    private static long lastCheckTime = 0;
    private static boolean lastCheckResult = false;
    
    /**
     * Check if internet connectivity is available.
     * Uses cached result for up to 5 seconds to avoid excessive checks.
     */
    public static boolean isInternetAvailable() {
        long now = System.currentTimeMillis();
        
        // Use cached result if recent
        if (now - lastCheckTime < CACHE_TIME) {
            return lastCheckResult;
        }
        
        lastCheckTime = now;
        lastCheckResult = performConnectivityCheck();
        
        return lastCheckResult;
    }
    
    private static boolean performConnectivityCheck() {
        try {
            // Try to connect to Vosk models page with short timeout
            URL url = new URL("https://alphacephei.com/vosk/models");
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.getInputStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
