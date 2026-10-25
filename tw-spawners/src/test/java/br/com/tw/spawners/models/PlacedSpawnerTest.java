package br.com.tw.spawners.models;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class PlacedSpawnerTest {

    @Test
    public void snapshotRemainsStableAfterSpawnerChanges() {
        PlacedSpawner spawner = new PlacedSpawner("world;1;64;2", UUID.randomUUID(),
                "Player", "PIG", 2L);

        PlacedSpawner snapshot = spawner.snapshot();
        spawner.setPendingPhysicalSpawn(150L);
        spawner.addStoredMoney(200.0);
        spawner.addStoredTokens(3L);
        spawner.addStack(1L);

        assertEquals(0.0, snapshot.getStoredMoney(), 0.0);
        assertEquals(0L, snapshot.getStoredTokens());
        assertEquals(2L, snapshot.getStackAmount());
        assertEquals(0L, snapshot.getPendingPhysicalSpawn());
        assertEquals(150L, spawner.snapshot().getPendingPhysicalSpawn());
    }
}
