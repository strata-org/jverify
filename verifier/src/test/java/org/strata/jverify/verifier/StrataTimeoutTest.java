package org.strata.jverify.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataTimeoutTest {

    private String saved;

    @BeforeEach
    void saveProperty() {
        saved = System.getProperty(StrataTimeout.PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (saved == null) {
            System.clearProperty(StrataTimeout.PROPERTY);
        } else {
            System.setProperty(StrataTimeout.PROPERTY, saved);
        }
    }

    @Test
    void aHangingProcessIsKilledAndReportedAsTimedOut() throws Exception {
        System.setProperty(StrataTimeout.PROPERTY, "1");
        var process = new ProcessBuilder("sleep", "30").start();
        try {
            var timeout = StrataTimeout.watch(process);
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "the watchdog did not kill the process");
            assertTrue(timeout.timedOut());
            assertEquals(1, timeout.seconds());
            assertTrue(timeout.reason().contains(StrataTimeout.PROPERTY));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void theKillReachesTheProcessDescendants() throws Exception {
        System.setProperty(StrataTimeout.PROPERTY, "1");
        var process = new ProcessBuilder("sh", "-c", "sleep 30 & wait").start();
        List<ProcessHandle> descendants = List.of();
        try {
            var timeout = StrataTimeout.watch(process);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(900);
            while (descendants.isEmpty() && System.nanoTime() < deadline) {
                descendants = process.descendants().toList();
                Thread.onSpinWait();
            }
            assertFalse(descendants.isEmpty(), "sh did not spawn its sleep before the timeout");
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "the watchdog did not kill the process");
            assertTrue(timeout.timedOut());
            for (var child : descendants) {
                child.onExit().get(5, TimeUnit.SECONDS);
                assertFalse(child.isAlive(), "descendant " + child.pid() + " survived the timeout");
            }
        } finally {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void aProcessThatFinishesInTimeIsNotReportedAsTimedOut() throws Exception {
        System.setProperty(StrataTimeout.PROPERTY, "5");
        var process = new ProcessBuilder("true").start();
        var timeout = StrataTimeout.watch(process);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertFalse(timeout.timedOut());
    }

    @Test
    void anUnsetOrEmptyPropertyMeansNoLimit() {
        System.clearProperty(StrataTimeout.PROPERTY);
        assertEquals(0, StrataTimeout.configuredSeconds());
        System.setProperty(StrataTimeout.PROPERTY, "");
        assertEquals(0, StrataTimeout.configuredSeconds());
    }

    @Test
    void aNonNumericPropertyIsRejected() throws IOException {
        System.setProperty(StrataTimeout.PROPERTY, "10m");
        var e = assertThrows(IllegalArgumentException.class, StrataTimeout::configuredSeconds);
        assertTrue(e.getMessage().contains(StrataTimeout.PROPERTY));
        assertTrue(e.getMessage().contains("10m"));
        var process = new ProcessBuilder("true").start();
        try {
            assertThrows(IllegalArgumentException.class, () -> StrataTimeout.watch(process));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void aNegativePropertyIsRejected() {
        System.setProperty(StrataTimeout.PROPERTY, "-1");
        assertThrows(IllegalArgumentException.class, StrataTimeout::configuredSeconds);
    }
}
