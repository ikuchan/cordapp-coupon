package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.template.states.CouponTokenType;
import com.template.contracts.PurchaseOrderContract;
import com.template.states.PurchaseOrderState;
// import net.corda.confidential.IdentitySyncFlow;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt.addMoveFungibleTokens;
import static com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemFlowUtilitiesKt.addNonFungibleTokensToRedeem;


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
            FlowSession session = initiateFlow(sellto);

            // Send sale request to buyer
            session.send(new SaleRequest(itemId, price));

            // subFlow(new IdentitySyncFlow.Receive(session));

            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherSession, ProgressTracker progressTracker) {
                    super(otherSession, progressTracker);
                }

                private TransactionState loadTransactionState(StateRef ref) {
                    try {
                        // SignedTransaction.getInputs() returns a list of
                        // StateRef. So we need to load corresponding TransactionState
                        // by calling ServiceHub.loadState().
                        return getServiceHub().loadState(ref);
                    } catch (TransactionResolutionException tre) {
                        // TODO: handling exceptions in lambda function
                        return null;
                    }
                }

                // As part of `SignTransactionFlow`, the contracts of the
                // transaction's input and output states are run automatically.
                // Inside `checkTransaction`, we define our own additional logic
                // for checking the received transaction. If `checkTransaction`
                // throws an exception, we'll refuse to sign.
                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException {
                    System.out.println("Verifying transaction on the seller side...");

                    StringBuffer str = new StringBuffer();

                    str.append("*************INPUTS***********\n");
                    stx.getTx().getInputs().forEach(it -> {
                        str.append("************************\n");
                        str.append(it.toString() + "\n\n");
                        try {
                            TransactionState ts = getServiceHub().loadState(it);
                            str.append(ts.toString() + "\n\n");
                        } catch (TransactionResolutionException tre) {
                            str.append("Failed to load transaction state " + it.toString() + "\n");
                        }
                    });

                    StateRef[] matchedStates = stx.getTx().getInputs().stream().filter(stateRef -> {
                        TransactionState ts = loadTransactionState(stateRef);
                        return (ts.getData() instanceof NonFungibleToken);
                    }).toArray(StateRef[]::new);

                    str.append("**********Number of coupons***********\n");
                    str.append( matchedStates.length + "\n");

                    int amountRequested = price;
                    if (matchedStates.length == 1) {
                        TransactionState ts = loadTransactionState(matchedStates[0]);
                        NonFungibleToken nonFungibleToken = (NonFungibleToken) ts.getData();
                        CouponTokenType couponTokenType = (CouponTokenType) nonFungibleToken.getIssuedTokenType().getTokenType();

                        PurchaseOrderState purchaseOrderState = (PurchaseOrderState) stx.getTx().getOutputStates().stream().filter(state -> {
                            return state instanceof PurchaseOrderState;
                        }).findAny().orElse(null);

                        if (purchaseOrderState == null) {
                            throw new FlowException("There should be exactly one PurchaseOrder state in output");
                        } else if (!purchaseOrderState.getItemId().equals(couponTokenType.getItemId())) {
                            throw new IllegalArgumentException(String.format(
                                    "Coupon for item (%s) cannot be used for purchase of item (%s)",
                                    couponTokenType.getItemId(),
                                    purchaseOrderState.getItemId()));
                        }


                        amountRequested -= Math.round(price * couponTokenType.getDiscount() / 100);
                    } else if (matchedStates.length > 1) {
                        throw new FlowException("There should be at most one coupon state in transaction");
                    }

                    str.append("*************OUTPUTS***********\n");
                    stx.getTx().getOutputStates().forEach(it -> {
                        str.append("************************\n");
                        str.append(it.toString() + "\n\n");

                    });

                    Party myself = getServiceHub().getMyInfo().getLegalIdentities().get(0);

                    stx.getTx().getOutputStates().stream().filter(it -> {
                         return (it instanceof FungibleToken &&
                                ((FungibleToken) it).getHolder().equals(myself));
                    });

                    long amountPaid = 0;
                    List<ContractState> outputStates = stx.getTx().getOutputStates();
                    for (int i = 0; i < outputStates.size(); i++) {
                        ContractState cs = outputStates.get(i);
                        if (cs instanceof FungibleToken &&
                                ((FungibleToken) cs).getHolder().equals(myself)) {

                            FungibleToken token = (FungibleToken) cs;
                            amountPaid += token.getAmount().getQuantity();
                        }
                    }

                    str.append("******AMOUNT REQUESTED: " + amountRequested + " **************\n\n");
                    str.append("******AMOUNT PAID: " + amountPaid + " **************\n\n");

                    if (amountRequested != amountPaid) {
                        throw new FlowException("Amount paid (" + amountPaid + " JPY) does not match sale price (" + amountRequested + " JPY)");
                    }
                }
            }

            // Respond to CollectSignatures request from buyer
            try{
                SecureHash hash = subFlow(new SignTxFlow(session, SignTransactionFlow.tracker())).getId();

                return subFlow(new ReceiveFinalityFlow(session, hash));
            } catch (FlowException ex) {
                throw ex;
            }

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
        public SignedTransaction call() throws FlowException, IllegalStateException {

            // Receive sale  request from Seller
            SaleRequest saleRequest = session.receive(SaleRequest.class).unwrap(it -> {
                return it;
            });

            Party sellerParty = session.getCounterparty();
            Party buyerParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Prepare for shared transaction
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

            // Initialize the amount we are going to pay to the price in sale request
            int paidAmount = saleRequest.getPrice();

            // Check if there's a coupon from the seller
            NonFungibleToken nonFungibleToken = BuyerSellerFlow.queryNonFungibleTokenByIssuer(getServiceHub(), sellerParty);

            if (nonFungibleToken != null) {
                CouponTokenType couponTokenType = (CouponTokenType) nonFungibleToken.getIssuedTokenType().getTokenType();
                addNonFungibleTokensToRedeem(
                        transactionBuilder,
                        getServiceHub(),
                        couponTokenType,
                        sellerParty
                );

                paidAmount -= Math.round(paidAmount * couponTokenType.getDiscount() / 100);
            }

            // Fungible TokenType for the money
            TokenType currencyTokenType = FiatCurrency.Companion.getInstance("JPY");
            PartyAndAmount partyAndAmount = new PartyAndAmount(
                    sellerParty,
                    new Amount(paidAmount, currencyTokenType));

            addMoveFungibleTokens(
                    transactionBuilder,
                    getServiceHub(),
                    ImmutableList.of(partyAndAmount),
                    buyerParty);

            PurchaseOrderState outputState = new PurchaseOrderState(sellerParty, buyerParty, saleRequest.getItemId());
            transactionBuilder
                    // .addOutputState(outputState, PurchaseOrderContract.ID)
                    .addCommand(
                    new PurchaseOrderContract.Commands.Issue(),
                    ImmutableList.of(sellerParty.getOwningKey(), buyerParty.getOwningKey()));

            // Set TimeWindow for the transaction
            Clock clock = getServiceHub().getClock();
            transactionBuilder.setTimeWindow(clock.instant(), Duration.ofSeconds(60));

            // subFlow(new IdentitySyncFlow.Send(session, transactionBuilder.toWireTransaction(getServiceHub())));

            // Verify the transaction locally first
            transactionBuilder.verify(getServiceHub());

            // Sign the transaction
            SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(transactionBuilder);



            // Collect signatures from other participants in the transaction
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(session)));

            return subFlow(new FinalityFlow(fullySignedTx, ImmutableList.of(session)));
        }
    }

    @StartableByRPC
    @InitiatingFlow
    public static class ShowCoupon extends FlowLogic<String> {
        private final Party issuer;

        public ShowCoupon(Party issuer) {
            this.issuer = issuer;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            StringBuffer output = new StringBuffer();

            NonFungibleToken nonFungibleToken = BuyerSellerFlow.queryNonFungibleTokenByIssuer(getServiceHub(), issuer);

            if (nonFungibleToken != null) {
                output.append(nonFungibleToken.toString() + "\n");
                output.append("Issuer = " + nonFungibleToken.getIssuer() + "\n");
                output.append("Holder = " + nonFungibleToken.getHolder() + "\n");
            }
            else {
                output.append("No Coupon found");
            }

            return output.toString();
        }
    }

    @Suspendable
    public static NonFungibleToken queryNonFungibleTokenByIssuer (ServiceHub serviceHub, Party issuer) {
        List<StateAndRef<NonFungibleToken>> nonFungibleStateRefs = serviceHub
                .getVaultService()
                .queryBy(NonFungibleToken.class)
                .getStates();

        StateAndRef<NonFungibleToken> match =
                nonFungibleStateRefs.stream()
                        .filter(stateAndRef -> {
                            NonFungibleToken nonFungibleToken = stateAndRef.getState().getData();
                            TokenType realTokenType = nonFungibleToken.getIssuedTokenType().getTokenType();

                            return (realTokenType instanceof CouponTokenType &&
                                nonFungibleToken.getIssuer().equals(issuer));
                        })
                        .findAny().orElse(null);

        return match != null ?
                (NonFungibleToken) match.getState().getData() :
                null;

    }

    @Suspendable
    public static CouponTokenType getCouponByLinearId (UUID linearId, ServiceHub serviceHub) throws FlowException {
        QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria(
                null,
                ImmutableList.of(linearId));

        StringBuffer output = new StringBuffer();

        List<StateAndRef<ContractState>> allStatesAndRefs =
                serviceHub
                    .getVaultService()
                    .queryBy(ContractState.class, criteria).getStates();

        allStatesAndRefs.forEach(stateAndRef -> {
            output.append("*************\n\n");
            output.append(stateAndRef.toString());
        });

        StateAndRef<ContractState> stateRef = allStatesAndRefs.get(0);
        NonFungibleToken nonFungibleTokenState = (NonFungibleToken) stateRef.getState().getData();
        return (CouponTokenType) nonFungibleTokenState.getIssuedTokenType().getTokenType();
    }
}
