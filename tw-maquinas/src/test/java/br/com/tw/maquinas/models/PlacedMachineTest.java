package br.com.tw.maquinas.models;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PlacedMachineTest {

    @Test
    public void snapshotRemainsStableAfterMachineChanges() {
        PlacedMachine machine = new PlacedMachine("world;1;64;2", UUID.randomUUID(),
                "Player", "CARVAO", 2L, 10L, 50.0);

        PlacedMachine snapshot = machine.snapshot();
        machine.addFuel(5L);
        machine.addStoredEarnings(25.0);
        machine.addStack(1L);

        assertEquals(10L, snapshot.getFuel());
        assertEquals(50.0, snapshot.getStoredEarnings(), 0.0);
        assertEquals(2L, snapshot.getStackAmount());
    }

    @Test
    public void batteryAndLubricantCyclesAreConsumedAndCopiedToSnapshots() {
        PlacedMachine machine = new PlacedMachine("world;1;64;2", UUID.randomUUID(),
                "Player", "CARVAO", 1L, 0L, 0.0, 1, 1, 1, 0.0, 4L, 5L);

        PlacedMachine snapshot = machine.snapshot();
        assertTrue(machine.consumeBatteryCycle());
        assertTrue(machine.consumeLubricantCycle());
        assertEquals(3L, machine.getBatteryCycles());
        assertEquals(4L, machine.getLubricantCycles());
        assertEquals(4L, snapshot.getBatteryCycles());
        assertEquals(5L, snapshot.getLubricantCycles());
        PlacedMachine empty = new PlacedMachine("world;1;64;2", UUID.randomUUID(),
                "Player", "CARVAO", 1L, 0L, 0.0, 1, 1, 1, 0.0, 0L, 0L);
        assertFalse(empty.consumeBatteryCycle());
        assertFalse(empty.consumeLubricantCycle());
    }

    @Test
    public void stackAndFuelAdditionsRejectOverflowWithoutChangingState() {
        PlacedMachine stack = new PlacedMachine("world;1;64;2", UUID.randomUUID(),
                "Player", "CARVAO", Long.MAX_VALUE, 0L, 0.0);
        try {
            stack.addStack(1L);
            fail("Esperava rejeição de overflow do stack.");
        } catch (ArithmeticException expected) {
            assertEquals(Long.MAX_VALUE, stack.getStackAmount());
        }

        PlacedMachine fuel = new PlacedMachine("world;1;64;2", UUID.randomUUID(),
                "Player", "CARVAO", 1L, Long.MAX_VALUE, 0.0);
        try {
            fuel.addFuel(1L);
            fail("Esperava rejeição de overflow do combustível.");
        } catch (ArithmeticException expected) {
            assertEquals(Long.MAX_VALUE, fuel.getFuel());
        }
    }

    @Test
    public void machineRejectsNonFiniteOrNegativeStoredEarnings() {
        PlacedMachine machine = new PlacedMachine("world;1;64;2", UUID.randomUUID(),
                "Player", "CARVAO", 1L, 0L, 0.0);
        try {
            machine.addStoredEarnings(Double.NaN);
            fail("Esperava rejeição de produção inválida.");
        } catch (IllegalArgumentException expected) {
            assertEquals(0.0, machine.getStoredEarnings(), 0.0);
        }

        try {
            machine.setStoredEarnings(-1.0);
            fail("Esperava rejeição de armazenamento negativo.");
        } catch (IllegalArgumentException expected) {
            assertEquals(0.0, machine.getStoredEarnings(), 0.0);
        }
    }
}
