package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

@InitiatingFlow
@StartableByRPC
public class IssueCash extends FlowLogic<SignedTransaction> {
    private final ProgressTracker progressTracker = new ProgressTracker();

    private final String currency;
    private final Long amount;
    private final Party recipient;

    public IssueCash(Long amount, String currency, Party recipient) {
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
        // Create a TokenType to represent JPY currency
        TokenType currencyType = FiatCurrency.Companion.getInstance(currency);

        // Make the JPY currency an IssuedTokenType so it has an issuer
        IssuedTokenType issuedCurrency = new IssuedTokenType(getOurIdentity(), currencyType);

        // Amount of JPY to create
        Amount<IssuedTokenType> amountToIssue = new Amount(amount, issuedCurrency);

        // FungibleToken is a State class that implements FungibleState interface
        FungibleToken fungibleToken = new FungibleToken(amountToIssue, recipient, TransactionUtilitiesKt.getAttachmentIdForGenericParam(currencyType));

        return subFlow(new IssueTokens(ImmutableList.of(fungibleToken)));
    }
}
