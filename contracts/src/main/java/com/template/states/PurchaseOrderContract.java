package com.template.states;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class PurchaseOrderContract implements Contract {
    public static String ID = "com.template.states.PurchaseOrderContract";

    public interface Commands extends CommandData {
        class Purchase implements Commands {}
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

    }
}
