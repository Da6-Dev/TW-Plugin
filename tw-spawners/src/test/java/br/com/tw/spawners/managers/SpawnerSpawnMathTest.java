package br.com.tw.spawners.managers;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SpawnerSpawnMathTest {

    @Test
    public void stackOfTenThousandGeneratesTenThousandWhenConfigured() {
        assertEquals(10_000L, SpawnerSpawnMath.amount(10_000L, 1, 10_000L));
    }

    @Test
    public void generationLimitIsAppliedAfterAmountUpgrade() {
        assertEquals(10_000L, SpawnerSpawnMath.amount(9_000L, 2, 10_000L));
        assertEquals(7_500L, SpawnerSpawnMath.amount(2_500L, 3, 10_000L));
    }

    @Test
    public void binomialSamplingRespectsCertainAndImpossibleResults() {
        Random random = new Random(42L);
        assertEquals(10_000L, SpawnerSpawnMath.sampleBinomial(10_000L, 1.0, random));
        assertEquals(0L, SpawnerSpawnMath.sampleBinomial(10_000L, 0.0, random));
    }

    @Test
    public void binomialSamplingIsBoundedAndHasReasonableMean() {
        Random random = new Random(1234L);
        long total = 0L;
        int samples = 500;
        for (int i = 0; i < samples; i++) {
            long result = SpawnerSpawnMath.sampleBinomial(1_000L, 0.2, random);
            assertTrue(result >= 0L && result <= 1_000L);
            total += result;
        }
        assertTrue(Math.abs(total / (double) samples - 200.0) < 4.0);
    }

    @Test
    public void rejectsInvalidGenerationInputs() {
        try {
            SpawnerSpawnMath.amount(-1L, 1, 10_000L);
            fail("Esperava erro para stack negativo.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("inválidos"));
        }
    }
}
