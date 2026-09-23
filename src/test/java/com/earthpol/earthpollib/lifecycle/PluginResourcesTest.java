package com.earthpol.earthpollib.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginResourcesTest {
    @Test
    void closesInReverseOrderAndContinuesAfterFailures() {
        PluginResources resources = new PluginResources();
        List<String> closed = new ArrayList<>();
        resources.onClose(() -> closed.add("database"));
        resources.onClose(() -> { closed.add("broken"); throw new IllegalStateException("failure"); });
        resources.onClose(() -> closed.add("scheduler"));

        IllegalStateException failure = assertThrows(IllegalStateException.class, resources::close);
        assertEquals(List.of("scheduler", "broken", "database"), closed);
        assertEquals(1, failure.getSuppressed().length);
        resources.close();
        assertEquals(3, closed.size());
    }

    @Test
    void lateRegistrationIsClosedAndRejected() {
        PluginResources resources = new PluginResources();
        resources.close();
        List<String> closed = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> resources.onClose(() -> closed.add("late")));
        assertEquals(List.of("late"), closed);
    }
}
