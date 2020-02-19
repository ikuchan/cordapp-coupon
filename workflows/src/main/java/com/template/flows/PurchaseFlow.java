package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.template.states.CouponTokenType;
import com.template.contracts.PurchaseOrderContract;
import com.template.states.PurchaseOrderState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.NetworkMapCache;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;


public class PurchaseFlow {
    private PurchaseFlow () {}

    @StartableByRPC
    @InitiatingFlow
    public static class PurchaseWithCoupon extends FlowLogic<SignedTransaction> {
        private final ProgressTracker progressTracker = new ProgressTracker();

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        private CouponTokenType getCouponToken() {
            List<StateAndRef<NonFungibleToken>> nonFungibleStateRefs = getServiceHub()
                    .getVaultService()
                    .queryBy(NonFungibleToken.class)
                    .getStates();

            StateAndRef<NonFungibleToken> inputStateRef =
                    nonFungibleStateRefs.stream().filter(stateAndRef -> {
                        NonFungibleToken nonFungibleToken = stateAndRef.getState().getData();
                        TokenType realTokenType = nonFungibleToken.getIssuedTokenType().getTokenType();

                        return (realTokenType instanceof CouponTokenType);
                    }).findAny().orElseThrow(() -> new IllegalArgumentException("No coupon found"));

            return (CouponTokenType) inputStateRef.getState().getData().getTokenType();
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            NetworkMapCache networkMapCache = getServiceHub().getNetworkMapCache();
            CordaX500Name nodeAName = new CordaX500Name("PartyA", "London", "GB");
            CordaX500Name nodeBName = new CordaX500Name("PartyB", "New York", "US");
            Party seller = networkMapCache.getPeerByLegalName(nodeAName);
            Party buyer = networkMapCache.getPeerByLegalName(nodeBName);

            // NodeInfo nodeA = getServiceHub().getNetworkMapCache().getNodeByLegalName(new CordaX500Name("PartyA", "New York", "US"));
            // System.out.println("*********Node A**********");
            // System.out.println(nodeA);
            // System.out.println(nodeA.getLegalIdentities().get(0));

            // System.out.println("*********Tests**********");
            // System.out.println(networkMapCache.getNodeByLegalName(nodeAName));
            // System.out.println(networkMapCache.getNodesByLegalName(nodeAName));
            //
            // List<NodeInfo> allNodes = networkMapCache.getAllNodes();
            // allNodes.forEach(nodeInfo -> {
            //     System.out.println("*********Legal Identities**********");
            //     System.out.println(nodeInfo.getLegalIdentities().get(0));
            // });

            System.out.println("*********Notary**********");
            System.out.println(notary);
            System.out.println("*********Seller Party**********");
            System.out.println(seller);
            System.out.println("*********Buyer Party**********");
            System.out.println(buyer);

            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

            // Output State
            PurchaseOrderState outputState = new PurchaseOrderState(seller, buyer, "book-101");

            List<PublicKey> requiredSigners = ImmutableList.of(
                    outputState.getSeller().getOwningKey(),
                    outputState.getBuyer().getOwningKey()
            );

            // What to do with input?
            // addNonFungibleTokensToRedeem(
            //         transactionBuilder,
            //         getServiceHub(),
            //         getCouponToken(),
            //         seller);

            transactionBuilder
                    .addOutputState(outputState, PurchaseOrderContract.ID)
                    .addCommand(new PurchaseOrderContract.Commands.Issue(), requiredSigners);

            transactionBuilder.verify(getServiceHub());

            SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(transactionBuilder);

            FlowSession flowSession = initiateFlow(seller);



            return null;
        }
    }
}
