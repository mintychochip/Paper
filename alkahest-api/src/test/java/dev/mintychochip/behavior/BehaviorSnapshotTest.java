package dev.mintychochip.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class BehaviorSnapshotTest {

    @Test
    void valueOverrideDistinguishesDefaultFromValue() {
        assertInstanceOf(ValueOverride.UseDefault.class, ValueOverride.useDefault());
        assertEquals("x", ((ValueOverride.Value<String>) ValueOverride.value("x")).value());
    }
}
