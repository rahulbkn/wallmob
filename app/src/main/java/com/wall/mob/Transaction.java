package com.wall.mob;

import java.io.Serializable;

public class Transaction implements Serializable {
    private String action;
    private int coinAmount;
    private long timestamp;

    public Transaction(String action, int coinAmount, long timestamp) {
        this.action = action;
        this.coinAmount = coinAmount;
        this.timestamp = timestamp;
    }

    public String getAction() {
        return action;
    }

    public int getCoinAmount() {
        return coinAmount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return action + ": " + (coinAmount > 0 ? "+" : "") + coinAmount + " coins";
    }
}
// test
