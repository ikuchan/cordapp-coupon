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
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import java.util.List;

public class CouponFlow {
    private CouponFlow() {}

    @StartableByRPC
    @InitiatingFlow
    static public class IssueCoupon extends FlowLogic<SignedTransaction> {
        private final ProgressTracker progressTracker = new ProgressTracker();

        private final Party recipient;

        private final String itemId;

        private final Integer discount;

        public IssueCoupon(Party recipient, String itemId, Integer discount) {
            this.recipient = recipient;
            this.itemId = itemId;
            this.discount = discount;
        }

        public IssueCoupon(Party recipient, String itemId) {
            this.recipient = recipient;
            this.itemId = itemId;
            this.discount = 10;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            CouponTokenType tokenType = new CouponTokenType(itemId, discount);
            IssuedTokenType issuedTokenType = new IssuedTokenType(getOurIdentity(), tokenType);

            NonFungibleToken nonFungibleToken = new NonFungibleToken(
                    issuedTokenType,
                    recipient,
                    new UniqueIdentifier(),
                    TransactionUtilitiesKt.getAttachmentIdForGenericParam(tokenType));

            // CouponState couponState = new CouponState(
            //         issuedTokenType,
            //         recipient,
            //         new UniqueIdentifier(),
            //         TransactionUtilitiesKt.getAttachmentIdForGenericParam(tokenType),
            //         "12345");

            return subFlow(new IssueTokens(ImmutableList.of(nonFungibleToken)));
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class RedeemCoupon extends FlowLogic<SignedTransaction> {

        private final ProgressTracker progressTracker = new ProgressTracker();

        private final Party issuer;
        // private final String

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

            List<StateAndRef<NonFungibleToken>> nonFungibleStateRefs = getServiceHub()
                    .getVaultService()
                    .queryBy(NonFungibleToken.class)
                    .getStates();

            // nonFungibleStateRefs.forEach(stateAndRef -> {
            //     NonFungibleToken nonFungibleToken = stateAndRef.getState().getData();
            //     System.out.println(stateAndRef);
            //
            //     TokenType realTokenType = nonFungibleToken.getIssuedTokenType().getTokenType();
            //
            //     if ( realTokenType instanceof CouponTokenType) {
            //         CouponTokenType c = (CouponTokenType)realTokenType;
            //         output.append("Coupon token for item " + c.getItemId() + " \n\n");
            //     }
            //
            // });


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


            // Inline approach
            // FlowSession issuerSession = initiateFlow(issuer);
            // subFlow(new RedeemCouponResponder(issuerSession));
            //
            // SignedTransaction tx = subFlow(new RedeemNonFungibleTokensFlow<TokenType>(inputState.getTokenType(), issuerSession, ImmutableList.of()));
            //
            // System.out.println("**********Transaction*********");
            // System.out.println(tx);
            // return tx;
        }
    }

    // @Suspendable
    // @StartableByService
    // @InitiatedBy(RedeemCoupon.class)
    // public static class RedeemCouponResponder extends FlowLogic<Void> {
    //
    //     private final ProgressTracker progressTracker = new ProgressTracker();
    //
    //     private FlowSession counterPartySession;
    //
    //     public RedeemCouponResponder(FlowSession session) {
    //         this.counterPartySession = session;
    //     }
    //
    //     @Override
    //     public Void call() throws FlowException {
    //
    //         counterPartySession.receive(SignedTransaction.class).unwrap(it -> {
    //             System.out.println("*******What is it******");
    //             System.out.println(it);
    //             return it;
    //         });
    //
    //         // return subFlow(new RedeemNonFungibleTokensHandler(counterPartySession));
    //         return null;
    //     }
    // }
}


