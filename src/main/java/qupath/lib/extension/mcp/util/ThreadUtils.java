/*
 * Copyright 2026 Zhaoqi Lu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qupath.lib.extension.mcp.util;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for handling threading in QuPath MCP extension.
 *
 * <p>QuPath uses JavaFX, which requires UI operations to run on the
 * JavaFX Application Thread. This class provides utilities for:
 * <ul>
 *   <li>Running tasks on the FX thread when needed</li>
 *   <li>Running background tasks in a thread pool</li>
 *   <li>Checking if running in headless mode</li>
 * </ul>
 */
public class ThreadUtils {

    private static final Logger logger = LoggerFactory.getLogger(ThreadUtils.class);

    private static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "QuPath-MCP-Worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Execute a task on the JavaFX Application Thread and wait for the result.
     *
     * <p>Required for any UI-related operations when QuPath is running in GUI mode.
     *
     * @param task The task to execute
     * @param timeoutSeconds Maximum time to wait (in seconds)
     * @return The result of the task
     * @throws Exception If the task fails or times out
     */
    public static <T> T runOnFxThread(Callable<T> task, long timeoutSeconds) throws Exception {
        // If already on FX thread, just execute
        if (Platform.isFxApplicationThread()) {
            return task.call();
        }

        // If FX platform is not running (headless), execute directly
        if (!isFxRunning()) {
            return task.call();
        }

        // Otherwise, submit to FX thread and wait
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Task timed out after " + timeoutSeconds + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Task was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException("Task failed", cause);
        }
    }

    /**
     * Execute a task on the JavaFX Application Thread and wait for the result
     * with a default timeout of 30 seconds.
     */
    public static <T> T runOnFxThread(Callable<T> task) throws Exception {
        return runOnFxThread(task, 30);
    }

    /**
     * Execute a task in a background thread.
     *
     * <p>Useful for long-running operations like script execution or image processing.
     *
     * @param task The task to execute
     * @return A CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> runInBackground(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, backgroundExecutor);
    }

    /**
     * Execute a Runnable in a background thread.
     */
    public static void runInBackground(Runnable task) {
        backgroundExecutor.submit(task);
    }

    /**
     * Check if running in headless mode (no GUI).
     *
     * @return true if QuPath is running without a GUI
     */
    public static boolean isHeadless() {
        return !isFxRunning();
    }

    /**
     * Check if JavaFX platform is running.
     */
    public static boolean isFxRunning() {
        try {
            return Platform.isFxApplicationThread() || isFxToolkitInitialized();
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean fxToolkitInitialized = false;

    private static boolean isFxToolkitInitialized() {
        return fxToolkitInitialized;
    }

    /**
     * Called when JavaFX toolkit is initialized.
     */
    public static void setFxToolkitInitialized(boolean initialized) {
        fxToolkitInitialized = initialized;
    }

    /**
     * Get the background executor for custom task submission.
     */
    public static ExecutorService getBackgroundExecutor() {
        return backgroundExecutor;
    }

    /**
     * Shutdown the background executor. Should be called when the extension is unloaded.
     */
    public static void shutdown() {
        backgroundExecutor.shutdown();
        try {
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Workaround for checking FX toolkit
    private static Object Toolkit = null;
    private static Object getDefaultToolkit() {
        if (Toolkit != null) {
            return Toolkit;
        }
        try {
            Class<?> tkClass = Class.forName("javafx.application.Toolkit");
            java.lang.reflect.Method method = tkClass.getMethod("getToolkit");
            Toolkit = method.invoke(null);
            return Toolkit;
        } catch (Exception e) {
            return null;
        }
    }
}
