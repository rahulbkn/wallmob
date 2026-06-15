package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class TransactionManager {
    private static final String PREFS_NAME = "GamePrefs";
    private static final String TRANS_COUNT_KEY = "trans_count";
    private static final int MAX_TRANSACTIONS = 100; // Limit to prevent excessive storage

    private final SharedPreferences sharedPrefs;

    public TransactionManager(Context context) {
        this.sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addTransaction(Transaction transaction) {
        List<Transaction> transactions = getTransactions();
        transactions.add(0, transaction); // Add new transaction at the beginning
        if (transactions.size() > MAX_TRANSACTIONS) {
            transactions.subList(MAX_TRANSACTIONS, transactions.size()).clear();
        }
        saveTransactions(transactions);
        Log.d("TransactionManager", "Added transaction: " + transaction);
    }

    public List<Transaction> getTransactions() {
        int count = sharedPrefs.getInt(TRANS_COUNT_KEY, 0);
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String str = sharedPrefs.getString("trans_" + i, null);
            if (str != null) {
                String[] parts = str.split("\\|", -1); // Use -1 to handle empty parts if needed
                if (parts.length == 3) {
                    try {
                        String action = parts[0];
                        int amount = Integer.parseInt(parts[1]);
                        long ts = Long.parseLong(parts[2]);
                        list.add(new Transaction(action, amount, ts));
                    } catch (NumberFormatException e) {
                        Log.e("TransactionManager", "Failed to parse transaction: " + str, e);
                    }
                }
            }
        }
        return list;
    }

    private void saveTransactions(List<Transaction> transactions) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(TRANS_COUNT_KEY, transactions.size());
        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            String str = t.getAction() + "|" + t.getCoinAmount() + "|" + t.getTimestamp();
            editor.putString("trans_" + i, str);
        }
        editor.apply();
    }
}