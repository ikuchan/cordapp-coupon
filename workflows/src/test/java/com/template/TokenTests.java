package com.template;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.flows.IssueCash;
import com.template.states.CouponTokenType;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.node.Corda;
import net.corda.testing.node.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Signed;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TokenTests {
    // Need to provide necessary CorDapp dependencies in the mock network here
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters(ImmutableList.of(
            TestCordapp.findCordapp("com.template.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts")
    )));

    private final StartedMockNode bankNode = network.createPartyNode(new CordaX500Name("Bank", "London", "GB"));
    private final StartedMockNode aliceNode = network.createPartyNode(new CordaX500Name("Alice", "London", "GB"));
    private final StartedMockNode bobNode = network.createPartyNode(new CordaX500Name("Bob", "London", "GB"));
    private final Party notary = network.getDefaultNotaryIdentity();
    private final Party bank = bankNode.getInfo().getLegalIdentities().get(0);
    private final Party alice = aliceNode.getInfo().getLegalIdentities().get(0);

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
    public void couponTest() throws Exception {
        CordaFuture<SignedTransaction> future = bankNode.startFlow(new IssueCash((long) 2000, alice));

        network.runNetwork();;

        SignedTransaction signedTransaction = future.get();

        List<StateAndRef<FungibleToken>> allStatesAndRefs = aliceNode.getServices().getVaultService().queryBy(FungibleToken.class).getStates();

        assertEquals(1, allStatesAndRefs.size());

        StateAndRef<FungibleToken> stateAndRef =  allStatesAndRefs.get(0);
        FungibleToken receivedToken = allStatesAndRefs.get(0).getState().getData();

        assertEquals(bank, stateAndRef.getState().getData().getIssuer());
        assertEquals(alice, stateAndRef.getState().getData().getHolder());
        assertEquals(2000, receivedToken.getAmount().getQuantity());
    }
}
