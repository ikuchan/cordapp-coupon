package com.template;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.states.CouponTokenType;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TokenTests {
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters(ImmutableList.of(
            TestCordapp.findCordapp("com.template.flows")
//            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
//            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
//            TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing")
    )));
    private final StartedMockNode bankNode = network.createPartyNode(null);
    private final StartedMockNode aliceNode = network.createPartyNode(null);
    private final StartedMockNode bobNode = network.createPartyNode(null);

//    private final TestIdentity bank = new TestIdentity(new CordaX500Name("Bank Japan", "", "GB"));
//    private final TestIdentity alice = new TestIdentity(new CordaX500Name("Alice", "", "Japan"));
//    private final TestIdentity bob = new TestIdentity(new CordaX500Name("Bob", "", "Japan"));
//    private MockServices ledgerServices = new MockServices(new TestIdentity(new CordaX500Name("TestId", "", "GB")));

    @Before
    public void setup() {
//        for (StartedMockNode node : ImmutableList.of(a, b)) {
//            node.registerInitiatedFlow(ExampleFlow.Acceptor.class);
//        }
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void dummyTest() throws Exception {
        TokenType tokenType = FiatCurrency.Companion.getInstance("JPY");
        Party bankParty = bankNode.getInfo().getLegalIdentities().get(0);
        Party aliceParty = aliceNode.getInfo().getLegalIdentities().get(0);

        IssuedTokenType issuedTokenType = new IssuedTokenType(bankParty, tokenType);

        Amount<IssuedTokenType> amount = new Amount(1000, issuedTokenType);

        FungibleToken money = new FungibleToken(
                amount,
                aliceParty,
                TransactionUtilitiesKt.getAttachmentIdForGenericParam(tokenType));

        CordaFuture<SignedTransaction> future = bankNode.startFlow(new IssueTokens(ImmutableList.of(money)));
        network.runNetwork();


//        SignedTransaction signedTransaction = future.get();
//        System.out.println( aliceNode.getServices().getVaultService().queryBy(FungibleToken.class).getStates());
//        System.out.println( bankNode.getServices().getVaultService().queryBy(FungibleToken.class).getStates());

    }

    @Test
    public void couponTest() throws Exception {
        Party bankParty = bankNode.getInfo().getLegalIdentities().get(0);
        Party aliceParty = aliceNode.getInfo().getLegalIdentities().get(0);

        CouponTokenType tokenType = new CouponTokenType("item-30202", 15);
        IssuedTokenType issuedTokenType = new IssuedTokenType(
                bankNode.getInfo().getLegalIdentities().get(0),
                tokenType);

        System.out.println(tokenType.getTokenClass().getSimpleName());
        if (tokenType.getTokenClass().equals(CouponTokenType.class)
        /*tokenType.getTokenClass().getSimpleName().equals("CouponTokenType")*/) {
            System.out.println("Hello Hello!");
            CouponTokenType coupon = (CouponTokenType) tokenType;
            System.out.println(coupon.getItemId());
        }

        NonFungibleToken nonFungibleToken = new NonFungibleToken(
                issuedTokenType,
                aliceParty,
                new UniqueIdentifier(),
                TransactionUtilitiesKt.getAttachmentIdForGenericParam(tokenType));

        CordaFuture<SignedTransaction> future = bankNode.startFlow(new IssueTokens(ImmutableList.of(nonFungibleToken)));
        network.runNetwork();

        // SignedTransaction signedTransaction = future.get();
        List<StateAndRef<ContractState>> allStatesAndRefs = bankNode.getServices().getVaultService().queryBy(ContractState.class).getStates();
        // List<StateAndRef<ContractState>> allStatesAndRefs = getServiceHub().getVaultService().queryBy(ContractState.class).getStates();

        // StringBuffer output= new StringBuffer("\n\n");
        // allStatesAndRefs.forEach(state -> {
        //
        //     if (state.getState().getData() instanceof TokenType) {
        //         // CouponTokenType c = (CouponTokenType) state.getState().getData();
        //         // System.out.println("Item Id is " + c.getItemId());
        //         output.append("Hello Hello**************");
        //     }
        //
        //     output.append(state.getState().getData().getClass() + " -- " + state.getState().getData().toString() + "\n");
        //     output.append(state.getState().toString() + "\n\n");
        // });

    }
}
