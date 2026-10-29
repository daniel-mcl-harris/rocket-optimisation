package com.danielh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class OptimiseCommandTest {

    @Test
    public void unknownCommandPrintsUsage() {
        assertUsage(new String[] {"optimize"});
        assertUsage(new String[] {});
        assertUsage(new String[] {"fly", "I350R"});
        try {
            OptimiseCommand.parse(new String[] {"optimize"});
            fail("expected usage");
        } catch (OptimiseCommand.UsageException e) {
            assertTrue(e.getMessage().contains("Valid commands: optimise"));
        }
    }

    @Test
    public void populationAndGenerationsAreIndependent() {
        OptimiseCommand.Args defaults = OptimiseCommand.parse(new String[] {"optimise"});
        assertEquals("I350R", defaults.motor);
        assertEquals(50, defaults.population);
        assertEquals(100, defaults.generations);

        OptimiseCommand.Args motorOnly = OptimiseCommand.parse(new String[] {"Optimise", "H128W-6"});
        assertEquals("H128W-6", motorOnly.motor);
        assertEquals(50, motorOnly.population);
        assertEquals(100, motorOnly.generations);

        OptimiseCommand.Args populationOnly = OptimiseCommand.parse(new String[] {"optimise", "H128W-6", "10"});
        assertEquals(10, populationOnly.population);
        assertEquals(100, populationOnly.generations);

        OptimiseCommand.Args both = OptimiseCommand.parse(new String[] {"optimise", "H128W-6", "10", "5"});
        assertEquals("H128W-6", both.motor);
        assertEquals(10, both.population);
        assertEquals(5, both.generations);

        OptimiseCommand.Args generationsOnly = OptimiseCommand.parse(new String[] {"optimise", "H128W-6", "--gens", "5"});
        assertEquals("H128W-6", generationsOnly.motor);
        assertEquals(50, generationsOnly.population);
        assertEquals(5, generationsOnly.generations);

        OptimiseCommand.Args named = OptimiseCommand.parse(new String[] {"optimise", "--pop", "8", "--gens=3"});
        assertEquals("I350R", named.motor);
        assertEquals(8, named.population);
        assertEquals(3, named.generations);

        OptimiseCommand.Args mixed = OptimiseCommand.parse(new String[] {"optimise", "I350R-P", "12", "--gens", "4"});
        assertEquals("I350R-P", mixed.motor);
        assertEquals(12, mixed.population);
        assertEquals(4, mixed.generations);
    }

    @Test
    public void nonPositiveCountsExitAsUsage() {
        assertUsage(new String[] {"optimise", "I350R", "abc"});
        assertUsage(new String[] {"optimise", "I350R", "10", "nope"});
        assertUsage(new String[] {"optimise", "I350R", "0", "5"});
        assertUsage(new String[] {"optimise", "I350R", "-3", "5"});
        assertUsage(new String[] {"optimise", "I350R", "10", "0"});
        assertUsage(new String[] {"optimise", "--pop", "0"});
        assertUsage(new String[] {"optimise", "--gens", "-1"});
        assertUsage(new String[] {"optimise", "--pop"});
        assertUsage(new String[] {"optimise", "--wind", "3"});
        assertUsage(new String[] {"optimise", "I350R", "10", "5", "--pop", "8"});
    }

    private static void assertUsage(String[] args) {
        try {
            OptimiseCommand.parse(args);
            fail("expected usage for " + String.join(" ", args));
        } catch (OptimiseCommand.UsageException e) {
            assertTrue(e.getMessage().contains("Usage: optimise"));
        }
    }
}
