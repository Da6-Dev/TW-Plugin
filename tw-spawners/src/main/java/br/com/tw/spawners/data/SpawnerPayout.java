package br.com.tw.spawners.data;

public final class SpawnerPayout {

    private final double balance;
    private final long tokens;
    private final double money;
    private final long paidTokens;

    public SpawnerPayout(double balance, long tokens, double money, long paidTokens) {
        this.balance = balance;
        this.tokens = tokens;
        this.money = money;
        this.paidTokens = paidTokens;
    }

    public double getBalance() { return balance; }
    public long getTokens() { return tokens; }
    public double getMoney() { return money; }
    public long getPaidTokens() { return paidTokens; }
}
