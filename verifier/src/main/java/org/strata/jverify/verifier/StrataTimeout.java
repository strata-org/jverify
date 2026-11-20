package org.strata.jverify.verifier;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class StrataTimeout {
    public static final String PROPERTY = "jverify.strata.timeoutSeconds";
    public static final String MESSAGE_PREFIX = "Strata did not finish within ";

    private final long seconds;
    private volatile boolean timedOut;

    private StrataTimeout(long seconds) {
        this.seconds = seconds;
    }

    public long seconds() {
        return seconds;
    }

    public boolean timedOut() {
        return timedOut;
    }

    public String reason() {
        return MESSAGE_PREFIX + seconds + " s (" + PROPERTY + ")";
    }

    public static long configuredSeconds() {
        var value = System.getProperty(PROPERTY);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        long seconds;
        try {
            seconds = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid(value, e);
        }
        if (seconds < 0) {
            throw invalid(value, null);
        }
        return seconds;
    }

    private static IllegalArgumentException invalid(String value, NumberFormatException cause) {
        return new IllegalArgumentException(PROPERTY + " must be a non-negative integer number of seconds"
                + " (0 or unset for no limit), got \"" + value + "\"", cause);
    }

    public static StrataTimeout watch(Process process) {
        var timeout = new StrataTimeout(configuredSeconds());
        if (timeout.seconds == 0) {
            return timeout;
        }
        var watchdog = new Thread(() -> {
            try {
                if (!process.waitFor(timeout.seconds, TimeUnit.SECONDS)) {
                    timeout.timedOut = true;
                    List<ProcessHandle> descendants = process.descendants().toList();
                    descendants.forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "strata-timeout");
        watchdog.setDaemon(true);
        watchdog.start();
        return timeout;
    }
}
