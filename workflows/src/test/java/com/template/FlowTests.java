package com.template;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.template.flows.BuyerSellerFlow;
import com.template.flows.CouponFlow;
import com.template.flows.IssueCash;
import com.template.states.PurchaseOrderState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.UnexpectedFlowEndException;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static net.corda.testing.common.internal.ParametersUtilitiesKt.testNetworkParameters;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;

public class FlowTests {
    private MockNetwork network;

    private StartedMockNode nodeA;
    private StartedMockNode nodeB;
    private StartedMockNode nodeShop;
    private StartedMockNode nodeBank;

    private Party partyA;
    private Party partyB;
    private Party partyShop;
    private Party partyBank;

    @Before
    public void setup() {
        network = new MockNetwork(new MockNetworkParameters(ImmutableList.of(
                TestCordapp.findCordapp("com.template.flows"),
                TestCordapp.findCordapp("com.template.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts")
        )).withNetworkParameters(testNetworkParameters(Collections.emptyList(), 4)));

        nodeA = network.createPartyNode(new CordaX500Name("PartyA", "Tokyo", "JP"));
        nodeB = network.createPartyNode(new CordaX500Name("PartyB", "Tokyo", "JP"));
        nodeShop = network.createPartyNode(new CordaX500Name("Shop", "Tokyo", "JP"));
        nodeBank = network.createPartyNode(new CordaX500Name("Bank", "Tokyo", "JP"));

        partyA = nodeA.getInfo().getLegalIdentities().get(0);
        partyB = nodeB.getInfo().getLegalIdentities().get(0);
        partyShop = nodeShop.getInfo().getLegalIdentities().get(0);
        partyBank = nodeBank.getInfo().getLegalIdentities().get(0);

        for (StartedMockNode node : ImmutableList.of(nodeBank, nodeShop, nodeA, nodeB)) {
            node.registerInitiatedFlow(BuyerSellerFlow.Buyer.class);
        }

        network.runNetwork();

    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    private CordaFuture<SignedTransaction> issueCash(Party recipient, int amount) {
        return nodeBank.startFlow(new IssueCash((long) amount, recipient));
    }

    private CordaFuture<SignedTransaction> issueCoupon(Party recipient, String itemId, int discount) {
        return nodeShop.startFlow(new CouponFlow.IssueCoupon(recipient, itemId, discount));
    }

    private List<StateAndRef<PurchaseOrderState>> queryPurchaseOrders(StartedMockNode node) {
        return node.getServices()
                .getVaultService()
                .queryBy(PurchaseOrderState.class)
                .getStates();
    }

    private List<StateAndRef<FungibleToken>> queryCash(StartedMockNode node) {
        return node.getServices()
                .getVaultService()
                .queryBy(FungibleToken.class)
                .getStates();
    }

    private long queryCashBalance (StartedMockNode node) {
        return queryCash(node)
                .stream()
                .map(element -> {
                    return element.getState().getData().getAmount().getQuantity();
                })
                .reduce((long)0, (subtotal, element) -> {
                    return subtotal + element;
                });
    }


    @Test
    public void purchase() {
        String itemId = "item123";
        int price = 700;

        issueCash(partyA, 2000);
        network.runNetwork();

        // Shop sells an item to PartyA
        CordaFuture<SignedTransaction> purchaseTx = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyA, itemId, price));
        network.runNetwork();

        // Checks correct PurchaseOrderState saved in nodeA
        List<StateAndRef<PurchaseOrderState>> recordsOnNodeA = queryPurchaseOrders(nodeA);
        assertEquals(1, recordsOnNodeA.size());
        assertEquals(itemId, recordsOnNodeA.get(0).getState().getData().getItemId());
        assertEquals(partyA.getName(), recordsOnNodeA.get(0).getState().getData().getBuyer().getName());
        assertEquals(partyShop.getName(), recordsOnNodeA.get(0).getState().getData().getSeller().getName());

        // Checks correct cash balance in nodeA
        assertEquals((2000 - price), queryCashBalance(nodeA));

        // Checks correct PurchaseOrderState saved in nodeB
        List<StateAndRef<PurchaseOrderState>> recordsOnNodeB = queryPurchaseOrders(nodeShop);
        assertEquals(1, recordsOnNodeB.size());
        assertEquals(itemId, recordsOnNodeB.get(0).getState().getData().getItemId());
        assertEquals(partyA.getName(), recordsOnNodeB.get(0).getState().getData().getBuyer().getName());
        assertEquals(partyShop.getName(), recordsOnNodeB.get(0).getState().getData().getSeller().getName());

        // Checks correct cash balance in nodeB
        assertEquals(price, queryCashBalance(nodeShop));
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void negativeItemPrice() throws Exception {
        String itemId = "item123";
        int price = -100;

        issueCash(partyA, 2000);
        network.runNetwork();

        CordaFuture<SignedTransaction> future = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyA, itemId, price));
        network.runNetwork();

        // Negative price should cause exception
        expectedException.expectCause(instanceOf(UnexpectedFlowEndException.class));
        future.get();
    }

    @Test
    public void purchaseWithWrongCoupon() throws Exception{
        String itemId = "item123";
        int price = 700;
        int discountPercentage = 50;

        issueCash(partyA, 2000);
        issueCoupon(partyA, "item456", discountPercentage);
        network.runNetwork();

        CordaFuture<SignedTransaction> future = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyA, itemId, price));
        network.runNetwork();

        expectedException.expectCause(instanceOf(Exception.class));
        future.get();
    }

    @Test
    public void attemptToSellToSelf() throws Exception{
        String itemId = "item123";
        int price = 700;

        issueCash(partyShop, 2000);
        network.runNetwork();

        CordaFuture<SignedTransaction> future = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyShop, itemId, price));
        network.runNetwork();

        expectedException.expectCause(instanceOf(Exception.class));
        future.get();
    }

    @Test
    public void purchaseWithNoCash() throws Exception{
        String itemId = "item123";
        int price = 5000;

        CordaFuture<SignedTransaction> future = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyA, itemId, price));
        network.runNetwork();

        // TODO: testing exception here doesn't work
        // Might have something to do with how TokenSDK handles attempts to move fungible tokens
        // attemptSpend() in TokenSelection.kt

        // future.get();
        // expectedException.expectCause(instanceOf(IllegalStateException.class));

        List<StateAndRef<PurchaseOrderState>> recordsOnNodeA = queryPurchaseOrders(nodeA);
        assertEquals(0, recordsOnNodeA.size());
        assertEquals(0, queryCashBalance(nodeA));
    }

    @Test
    public void purchaseWithInsufficientCash() throws Exception{
        String itemId = "item123";
        int price = 5000;

        issueCash(partyA, 2000);
        network.runNetwork();

        CordaFuture<SignedTransaction> future = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyA, itemId, price));
        network.runNetwork();

        // Checks NO PurchaseOrderState saved in nodeA
        List<StateAndRef<PurchaseOrderState>> recordsOnNodeA = queryPurchaseOrders(nodeA);
        assertEquals(0, recordsOnNodeA.size());
        assertEquals(2000, queryCashBalance(nodeA));
    }

    @Test
    public void purchaseWithCoupon() {
        String itemId = "item123";
        int price = 700;
        int discountPercentage = 50;

        issueCash(partyA, 2000);
        issueCoupon(partyA, "item123", discountPercentage);
        network.runNetwork();

        CordaFuture<SignedTransaction> purchaseTx = nodeShop.startFlow(new BuyerSellerFlow.Seller(partyA, itemId, price));
        network.runNetwork();

        // Checks correct PurchaseOrderState saved in nodeA
        List<StateAndRef<PurchaseOrderState>> recordsOnNodeA = queryPurchaseOrders(nodeA);
        assertEquals(1, recordsOnNodeA.size());
        assertEquals(itemId, recordsOnNodeA.get(0).getState().getData().getItemId());
        assertEquals(partyA.getName(), recordsOnNodeA.get(0).getState().getData().getBuyer().getName());
        assertEquals(partyShop.getName(), recordsOnNodeA.get(0).getState().getData().getSeller().getName());

        // Checks correct cash balance in nodeA
        int discountedPrice = price - Math.round(price * discountPercentage / 100);
        assertEquals((2000 - discountedPrice), queryCashBalance(nodeA));

        // Checks correct PurchaseOrderState saved in nodeB
        List<StateAndRef<PurchaseOrderState>> recordsOnNodeB = queryPurchaseOrders(nodeShop);
        assertEquals(1, recordsOnNodeB.size());
        assertEquals(itemId, recordsOnNodeB.get(0).getState().getData().getItemId());
        assertEquals(partyA.getName(), recordsOnNodeB.get(0).getState().getData().getBuyer().getName());
        assertEquals(partyShop.getName(), recordsOnNodeB.get(0).getState().getData().getSeller().getName());

        // Checks correct cash balance in nodeB
        assertEquals(discountedPrice, queryCashBalance(nodeShop));
    }


}
