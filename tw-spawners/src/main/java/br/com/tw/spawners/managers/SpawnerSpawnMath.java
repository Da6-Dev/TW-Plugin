package br.com.tw.spawners.managers;

public final class SpawnerSpawnMath {

    private SpawnerSpawnMath() {
    }

    public static long amount(long stackAmount, int amountLevel, long maxPerCycle) {
        if (stackAmount < 0L || amountLevel < 0 || maxPerCycle < 1L) {
            throw new IllegalArgumentException("Valores inválidos para calcular a geração do spawner.");
        }
        if (stackAmount == 0L || amountLevel == 0) return 0L;
        if (stackAmount > maxPerCycle / amountLevel) return maxPerCycle;
        return stackAmount * amountLevel;
    }

    public static long sampleBinomial(long trials, double probability, java.util.Random random) {
        if (trials < 0L || !Double.isFinite(probability) || probability < 0.0 || probability > 1.0
                || random == null) {
            throw new IllegalArgumentException("Parâmetros inválidos para o sorteio de produção.");
        }
        if (trials == 0L || probability == 0.0) return 0L;
        if (probability == 1.0) return trials;
        if (probability > 0.5) {
            return trials - sampleBinomial(trials, 1.0 - probability, random);
        }

        long successes = 0L;
        long remaining = trials;
        double logFailure = Math.log1p(-probability);
        while (remaining > 0L) {
            double sample = random.nextDouble();
            long failures = (long) Math.floor(Math.log1p(-sample) / logFailure);
            if (failures >= remaining) break;
            successes++;
            remaining -= failures + 1L;
        }
        return successes;
    }
}
