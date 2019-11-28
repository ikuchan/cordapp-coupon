package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ComputationException;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemNonFungibleTokensFlow;
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlow;
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlowHandler;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokensHandler;
import com.template.states.CouponTokenType;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import java.util.List;
import java.util.UUID;

public class CouponFlow {
    private CouponFlow() {}

    @StartableByRPC
    @InitiatingFlow
    static public class IssueCoupon extends FlowLogic<SignedTransaction> {
        private final ProgressTracker progressTracker = new ProgressTracker();

        static final int DEFAULT_DISCOUNT_PERCENTAGE = 10;

        static final String DEFAULT_ITEM = "dummy-item-101";

        private final Party recipient;

        private final String itemId;

        private final Integer discount;

        public IssueCoupon(Party to, String itemId, Integer discount) {
            this.recipient = to;
            this.itemId = itemId;
            this.discount = discount;
        }

        public IssueCoupon(Party to, String itemId) {
            this.recipient = to;
            this.itemId = itemId;
            this.discount = 10;
        }

        public IssueCoupon(Party to, Integer discount) {
            this.recipient = to;
            this.itemId = DEFAULT_ITEM;
            this.discount = discount;
        }

        public IssueCoupon(Party to) {
            this.recipient = to;
            this.itemId = DEFAULT_ITEM;
            this.discount = 10;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            if (discount < 0 || discount >= 100) {
                throw new FlowException("Discount rate must be a positve integer smaller than 100");
            }

            CouponTokenType tokenType = new CouponTokenType(itemId, discount);
            IssuedTokenType issuedTokenType = new IssuedTokenType(getOurIdentity(), tokenType);

            NonFungibleToken nonFungibleToken = new NonFungibleToken(
                    issuedTokenType,
                    recipient,
                    new UniqueIdentifier(),
                    TransactionUtilitiesKt.getAttachmentIdForGenericParam(tokenType));

            return subFlow(new IssueTokens(ImmutableList.of(nonFungibleToken)));
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class RedeemCoupon extends FlowLogic<SignedTransaction> {

        private final ProgressTracker progressTracker = new ProgressTracker();

        private final Party issuer;

        public RedeemCoupon(Party issuer) {
            this.issuer = issuer;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            StringBuffer output = new StringBuffer();

            // Query for non-fungible token states
            List<StateAndRef<NonFungibleToken>> nonFungibleStateRefs = getServiceHub()
                    .getVaultService()
                    .queryBy(NonFungibleToken.class)
                    .getStates();

            // Find any non-fungible token state of CouponToken
            StateAndRef<NonFungibleToken> inputStateRef =
                    nonFungibleStateRefs.stream().filter(stateAndRef -> {
                        NonFungibleToken nonFungibleToken = stateAndRef.getState().getData();
                        TokenType realTokenType = nonFungibleToken.getIssuedTokenType().getTokenType();

                        return (realTokenType instanceof CouponTokenType);
                    }).findAny().orElseThrow(() -> new IllegalArgumentException("No coupon found"));

            NonFungibleToken inputState = inputStateRef.getState().getData();
            System.out.println("**********Token*********");
            System.out.println(inputState.getTokenType());
            System.out.println("**********Issuer*********");
            System.out.println(issuer);

            return subFlow(new RedeemNonFungibleTokens(inputState.getTokenType(), issuer));
        }
    }

    // @StartableByRPC
    // @InitiatingFlow
    // public static class ShowCoupon extends FlowLogic<String> {
    //     private final String id;
    //
    //     public ShowCoupon(String id) {
    //         this.id = id;
    //     }
    //
    //     @Override
    //     @Suspendable
    //     public String call() throws FlowException {
    //         UUID uuid = UUID.fromString(id);
    //         // UniqueIdentifier.Companion.fromString(id);
    //
    //         QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria(
    //                 null,
    //                 ImmutableList.of(uuid));
    //
    //         StringBuffer output = new StringBuffer();
    //
    //         output.append(UniqueIdentifier.Companion.fromString(id).getId());
    //         output.append(uuid.toString());
    //
    //         List<StateAndRef<ContractState>> allStatesAndRefs = getServiceHub().
    //                 getVaultService().
    //                 queryBy(ContractState.class, criteria).getStates();
    //
    //         allStatesAndRefs.forEach(stateAndRef -> {
    //             output.append("*************\n\n");
    //             output.append(stateAndRef.toString());
    //         });
    //
    //         return output.toString();
    //     }
    // }
}


