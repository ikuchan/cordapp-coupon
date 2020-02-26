package com.template;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.template.flows.BuyerSellerFlow;
import com.template.flows.IssueCash;
import com.template.states.PurchaseOrderState;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.Vault;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.node.TestCordapp;
import net.corda.testing.node.User;
import org.junit.Test;
import rx.Observable;

import static net.corda.testing.core.ExpectKt.expect;
import static net.corda.testing.core.ExpectKt.expectEvents;

import java.util.*;

import static java.util.Arrays.asList;
import static net.corda.testing.common.internal.ParametersUtilitiesKt.testNetworkParameters;
import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;

public class DriverBasedTest {
    private final TestIdentity bank = new TestIdentity(new CordaX500Name("Bank", "", "GB"));
    private final TestIdentity consumer = new TestIdentity(new CordaX500Name("Consumer", "", "US"));
    private final TestIdentity shop = new TestIdentity(new CordaX500Name("Shop", "", "US"));

    final List<User> rpcUsers = ImmutableList.of(
            new User("user1", "test", ImmutableSet.of("ALL")));

    @Test
    public void CashIssueTest() {
        driver(new DriverParameters()
                .withIsDebug(true)
                .withStartNodesInProcess(true)
                .withNetworkParameters(testNetworkParameters(Collections.emptyList(), 4))
                .withCordappsForAllNodes(asList(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts")
                )), dsl -> {

            // Start nodes and wait for them both to be ready.
            List<CordaFuture<NodeHandle>> handleFutures = ImmutableList.of(
                    dsl.startNode(new NodeParameters().withProvidedName(bank.getName()).withRpcUsers(rpcUsers)),
                    dsl.startNode(new NodeParameters().withProvidedName(consumer.getName()).withRpcUsers(rpcUsers))
            );

            try {
                NodeHandle bankHandle = handleFutures.get(0).get();
                CordaRPCClient rpcClientBank = new CordaRPCClient(bankHandle.getRpcAddress());
                CordaRPCOps rpcProxyBank = rpcClientBank.start("user1", "test").getProxy();

                NodeHandle consumerHandle = handleFutures.get(1).get();
                CordaRPCClient rpcClientConsumer = new CordaRPCClient(consumerHandle.getRpcAddress());
                CordaRPCOps rpcProxyConsumer = rpcClientConsumer.start("user1", "test").getProxy();

                Observable<Vault.Update<FungibleToken>> consumerCashVaultUpdates = rpcProxyConsumer.vaultTrack(FungibleToken.class).getUpdates();

                // Bank issues 1000 JPY to Consumer
                rpcProxyBank.startFlowDynamic(
                        IssueCash.class,
                        new Long(1000),
                        consumerHandle.getNodeInfo().getLegalIdentities().get(0)
                ).getReturnValue().get();

                @SuppressWarnings("unchecked")
                Class<Vault.Update<FungibleToken>> cashUpdateClass = (Class<Vault.Update<FungibleToken>>)(Class<?>)Vault.Update.class;

                // Consumer is expected to see an update of produced state for received 1000 JPY
                expectEvents(consumerCashVaultUpdates, true, () ->
                        expect(cashUpdateClass,
                                update -> true,
                                update -> {
                                    Amount<IssuedTokenType> amount = update.getProduced().iterator().next().getState().getData().getAmount();
                                    assertEquals(1000, amount.getQuantity());
                                    return null;
                                })
                );
            } catch (Exception e) {
                throw new RuntimeException("Caught exception during test: ", e);
            }

            return null;
        });
    }

    @Test
    public void PurchaseTest() {
        driver(new DriverParameters()
                .withIsDebug(true)
                .withStartNodesInProcess(true)
                .withNetworkParameters(testNetworkParameters(Collections.emptyList(), 4))
                .withCordappsForAllNodes(asList(
                        TestCordapp.findCordapp("com.template.flows"),
                        TestCordapp.findCordapp("com.template.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts")
                )), dsl -> {

            // Start nodes and wait for them both to be ready.
            List<CordaFuture<NodeHandle>> handleFutures = ImmutableList.of(
                    dsl.startNode(new NodeParameters().withProvidedName(bank.getName()).withRpcUsers(rpcUsers)),
                    dsl.startNode(new NodeParameters().withProvidedName(consumer.getName()).withRpcUsers(rpcUsers)),
                    dsl.startNode(new NodeParameters().withProvidedName(shop.getName()).withRpcUsers(rpcUsers))
            );

            try {
                NodeHandle bankHandle = handleFutures.get(0).get();
                CordaRPCClient rpcClientBank = new CordaRPCClient(bankHandle.getRpcAddress());
                CordaRPCOps rpcProxyBank = rpcClientBank.start("user1", "test").getProxy();

                NodeHandle consumerHandle = handleFutures.get(1).get();
                CordaRPCClient rpcClientConsumer = new CordaRPCClient(consumerHandle.getRpcAddress());
                CordaRPCOps rpcProxyConsumer = rpcClientConsumer.start("user1", "test").getProxy();

                NodeHandle shopHandle = handleFutures.get(2).get();
                CordaRPCClient rpcClientShop = new CordaRPCClient(shopHandle.getRpcAddress());
                CordaRPCOps rpcProxyShop = rpcClientShop.start("user1", "test").getProxy();

                Observable<Vault.Update<FungibleToken>> shopCashVaultUpdates = rpcProxyShop.vaultTrack(FungibleToken.class).getUpdates();
                Observable<Vault.Update<PurchaseOrderState>> consumerPurchaseOrderVaultUpdates = rpcProxyConsumer.vaultTrack(PurchaseOrderState.class).getUpdates();


                // Bank issues 1000 JPY to Consumer
                rpcProxyBank.startFlowDynamic(
                        IssueCash.class,
                        new Long(1000),
                        consumerHandle.getNodeInfo().getLegalIdentities().get(0)
                ).getReturnValue().get();

                @SuppressWarnings("unchecked")
                Class<Vault.Update<PurchaseOrderState>> purchaseOrderUpdateClass = (Class<Vault.Update<PurchaseOrderState>>)(Class<?>)Vault.Update.class;

                @SuppressWarnings("unchecked")
                Class<Vault.Update<FungibleToken>> cashUpdateClass = (Class<Vault.Update<FungibleToken>>)(Class<?>)Vault.Update.class;

                // Shop sells "book-123" to Consumer
                rpcProxyShop.startFlowDynamic(
                        BuyerSellerFlow.Seller.class,
                        consumerHandle.getNodeInfo().getLegalIdentities().get(0),
                        "book-123",
                        200
                );

                // Consumer is expected to see a produced Purchase Order
                expectEvents(consumerPurchaseOrderVaultUpdates, true, () ->
                        expect(purchaseOrderUpdateClass, update -> true, update -> {
                            System.out.println("Consumer got vault update of " + update);
                            PurchaseOrderState purchaseOrderState = update.getProduced().iterator().next().getState().getData();
                            assertEquals("book-123", purchaseOrderState.getItemId());
                            assertEquals(shopHandle.getNodeInfo().getLegalIdentities().get(0).getOwningKey(), purchaseOrderState.getSeller().getOwningKey());
                            assertEquals(consumerHandle.getNodeInfo().getLegalIdentities().get(0).getOwningKey(), purchaseOrderState.getBuyer().getOwningKey());
                            return null;
                        })
                );

                // Shop is expected to receive 200 JPY
                expectEvents(shopCashVaultUpdates, true, () ->
                        expect(cashUpdateClass,
                                update -> true,
                                update -> {
                                    System.out.println("Shop got cash vault update of " + update);
                                    Amount<IssuedTokenType> amount = update.getProduced().iterator().next().getState().getData().getAmount();
                                    assertEquals(200, amount.getQuantity());
                                    return null;
                                })
                );
            } catch (Exception e) {
                throw new RuntimeException("Caught exception during test: ", e);
            }

            return null;
        });
    }
}