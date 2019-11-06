package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.template.states.PurchaseOrderContract;
import com.template.states.PurchaseOrderState;
import net.corda.core.contracts.Amount;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.time.Clock;
import java.time.Duration;

import static com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt.addMoveFungibleTokens;


public class BuyerSellerFlow {
    private BuyerSellerFlow () {}

    @CordaSerializable
    private static class SaleRequest {
        private final String itemId;
        private final int price;

        private SaleRequest(String itemId, int price) {
            this.itemId = itemId;
            this.price = price;
        }

        public String getItemId() { return itemId; }

        public int getPrice() { return price; }

        @Override
        public String toString() {
            return itemId + " for " + price;
        }
    }

    @StartableByRPC
    @InitiatingFlow
    public static class Seller extends FlowLogic<SignedTransaction> {
        private final ProgressTracker progressTracker = new ProgressTracker();

        private final Party sellto;
        private final String itemId;
        private final int price;

        public Seller(Party sellto, String itemId, int price) {
            this.sellto = sellto;
            this.itemId = itemId;
            this.price = price;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            // Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            FlowSession session = initiateFlow(sellto);

            // Send sale request to buyer
            session.send(new SaleRequest(itemId, price));

            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherSession, ProgressTracker progressTracker) {
                    super(otherSession, progressTracker);
                }

                // As part of `SignTransactionFlow`, the contracts of the
                // transaction's input and output states are run automatically.
                // Inside `checkTransaction`, we define our own additional logic
                // for checking the received transaction. If `checkTransaction`
                // throws an exception, we'll refuse to sign.
                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException {
                    // Whatever checking you want to do...
                }
            }

            // Respond to CollectSignatures request from buyer
            SecureHash hash = subFlow(new SignTxFlow(session, SignTransactionFlow.tracker())).getId();

            return subFlow(new ReceiveFinalityFlow(session, hash));
        }
    }

    @InitiatedBy(Seller.class)
    public static class Buyer extends FlowLogic<SignedTransaction> {
        private FlowSession session;

        public Buyer(FlowSession session) {
            this.session = session;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {

            // Receive sale  request from Seller
            SaleRequest saleRequest = session.receive(SaleRequest.class).unwrap(it -> {
                return it;
            });

            Party sellerParty = session.getCounterparty();
            Party buyerParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Prepare for shared transaction
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

            // Fungible TokenType for the money
            TokenType currencyTokenType = FiatCurrency.Companion.getInstance("JPY");
            PartyAndAmount partyAndAmount = new PartyAndAmount(
                    sellerParty,
                    new Amount(saleRequest.price, currencyTokenType));

            addMoveFungibleTokens(
                    transactionBuilder,
                    getServiceHub(),
                    ImmutableList.of(partyAndAmount),
                    buyerParty);

            PurchaseOrderState outputState = new PurchaseOrderState(sellerParty, buyerParty, saleRequest.getItemId());
            transactionBuilder
                    .addOutputState(outputState, PurchaseOrderContract.ID)
                    .addCommand(
                    new PurchaseOrderContract.Commands.Purchase(),
                    ImmutableList.of(sellerParty.getOwningKey(), buyerParty.getOwningKey())
                    );

            // Set TimeWindow for the transaction
            Clock clock = getServiceHub().getClock();
            transactionBuilder.setTimeWindow(clock.instant(), Duration.ofSeconds(60));

            // Verify the transaction locally first
            transactionBuilder.verify(getServiceHub());

            // Sign the transaction
            SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(transactionBuilder);

            // Collect signatures from other participants in the transaction
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(session)));

            return subFlow(new FinalityFlow(fullySignedTx, ImmutableList.of(session)));
        }


    }
}
