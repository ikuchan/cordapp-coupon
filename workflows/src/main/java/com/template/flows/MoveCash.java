package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

@InitiatingFlow
@StartableByRPC
public class MoveCash extends FlowLogic<SignedTransaction> {
    private final ProgressTracker progressTracker = new ProgressTracker();

    private final String currency;
    private final Long amount;
    private final Party recipient;

    public MoveCash(Long amount, String currency, Party recipient) {
        this.amount = amount;
        this.currency = currency;
        this.recipient = recipient;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
       // getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

       // Debug Only - prints out all FungibleToken states stored in the vault of current node
       // List<StateAndRef<FungibleToken>> cashStatesAndRefs = getServiceHub().getVaultService().queryBy(FungibleToken.class).getStates();
       // System.out.println("*CashStateRefs*");
       // System.out.println(cashStatesAndRefs);

        // It's important NOT to use IssuedTokenType here
        TokenType tokenTypeToMove = FiatCurrency.Companion.getInstance(currency);
        PartyAndAmount partyAndAmount = new PartyAndAmount(recipient, new Amount (amount, tokenTypeToMove));

        return subFlow(new MoveFungibleTokens(partyAndAmount));
    }
}
