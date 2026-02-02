package atri.palaash.jvosk.models;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages active downloads with cancellation support.
 * Tracks downloads across multiple dialog instances.
 */
public class DownloadManager {
    
    private static final DownloadManager INSTANCE = new DownloadManager();
    
    private CompletableFuture<?> currentDownload = null;
    private AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private String currentDownloadName = null;
    
    private DownloadManager() {
    }
    
    public static DownloadManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register an active download.
     */
    public synchronized void setActiveDownload(String modelName, CompletableFuture<?> future) {
        this.currentDownload = future;
        this.currentDownloadName = modelName;
        this.cancelRequested.set(false);
    }
    
    /**
     * Check if there's an active download.
     */
    public synchronized boolean hasActiveDownload() {
        return currentDownload != null && !currentDownload.isDone();
    }
    
    /**
     * Get the name of the currently downloading model.
     */
    public synchronized String getActiveDownloadName() {
        return currentDownloadName;
    }
    
    /**
     * Request cancellation of the current download.
     */
    public synchronized void cancelCurrentDownload() {
        cancelRequested.set(true);
        if (currentDownload != null) {
            currentDownload.cancel(true);
        }
    }
    
    /**
     * Check if cancellation was requested.
     * Used during downloads to allow interruption.
     */
    public boolean isCancellationRequested() {
        return cancelRequested.get();
    }
    
    /**
     * Clear the current download when complete or cancelled.
     */
    public synchronized void clearActiveDownload() {
        currentDownload = null;
        currentDownloadName = null;
        cancelRequested.set(false);
    }
    
    /**
     * Wait for the current download to complete.
     */
    public synchronized void waitForActiveDownload() throws InterruptedException {
        if (currentDownload != null) {
            try {
                currentDownload.join();
            } catch (Exception ignored) {
                // Download already completed or cancelled
            }
        }
    }
}
