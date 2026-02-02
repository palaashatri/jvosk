package atri.palaash.jvosk.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utilities for checking network connectivity.
 */
public class NetworkUtils {
    
    private static final long CACHE_TIME = 5000; // 5 seconds
    private static final AtomicLong lastCheckTime = new AtomicLong(0);
    private static volatile boolean lastCheckResult = false;
    
    /**
     * Check if internet connectivity is available.
     * Uses cached result for up to 5 seconds to avoid excessive checks.
     */
    public static synchronized boolean isInternetAvailable() {
        long now = System.currentTimeMillis();
        
        // Use cached result if recent
        if (now - lastCheckTime.get() < CACHE_TIME) {
            return lastCheckResult;
        }
        
        lastCheckTime.set(now);
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
