package com.panda.fulldraw.client;

/**
 * Dev runs only: the Architectury dev launcher leaves a non-daemon thread pool behind, so after Minecraft shuts down the
 * JVM never exits (it holds the dev jars, and the next build can't replace them), and on Fabric vanilla's shutdown
 * watchdog writes a crash report 15 s after {@code main} returns. Once the client is stopping, a daemon thread waits for
 * the client thread to end (the world is saved by then) and halts the JVM before the watchdog fires. Released jars
 * never arm it.
 */
public final class DevExit {
    /** Set by Architectury's dev launcher, absent in a real game. */
    private static final boolean DEV = System.getProperty("architectury.main.class") != null;

    private static boolean armed;

    private DevExit() {}

    /** Call on the client thread when the client starts shutting down. */
    public static synchronized void onClientStopping() {
        if (!DEV || armed) return;
        armed = true;
        Thread client = Thread.currentThread();
        Thread halt = new Thread(() -> {
            try {
                client.join(20_000);
                Thread.sleep(1_000);
            } catch (InterruptedException ignored) {
            }
            // halt skips shutdown hooks, so flush the log first: its last lines (a test run's verdict) would be lost
            org.apache.logging.log4j.LogManager.shutdown();
            Runtime.getRuntime().halt(0);
        }, "fulldraw dev exit");
        halt.setDaemon(true);
        halt.start();
    }
}
