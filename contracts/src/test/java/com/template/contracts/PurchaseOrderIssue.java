package com.template.contracts;

import com.google.common.collect.ImmutableList;
import com.template.states.PurchaseOrderState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.security.PublicKey;
import java.util.List;

import static com.template.utils.Mocks.*;
import static net.corda.testing.node.NodeTestUtils.transaction;

public class PurchaseOrderIssue {
    private final MockServices ledgerServices = new MockServices(
            ImmutableList.of("com.r3.corda.lib.tokens.contracts", "com.template.contracts")
    );

    public interface Commands extends CommandData {
        class DummyCommand implements PurchaseOrderIssue.Commands {}
    }

    /**
     * Checks PurchaseOrderContract implements Contract interface
     */
    @Test
    public void implementsContract() {
        assert (new PurchaseOrderContract() instanceof Contract);
    }

    /**
     * Checks exactly one IssueCommand is in transaction.
     */
    @Test
    public void includesIssueCommand() {
        PurchaseOrderState output = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item123");
        List<PublicKey> signers = ImmutableList.of(partyA.getPublicKey(), partyB.getPublicKey());

        transaction(ledgerServices, tx -> {
            // Wrong command, fail.
            tx.tweak(tw -> {
                tw.command(signers, new Commands.DummyCommand());
                return tw.fails();
            });

            // Too many Issue commands, fail.
            tx.tweak(tw -> {
                tw.command(signers, new PurchaseOrderContract.Commands.Issue());
                tw.command(signers, new PurchaseOrderContract.Commands.Issue());
                return tw.fails();
            });

            tx.command(signers, new PurchaseOrderContract.Commands.Issue());
            tx.output(PurchaseOrderContract.ID, output);
            return tx.verifies();
        });
    }

    /**
     * Checks no Purchase order as input state
     */
    @Test
    public void includesNoPurchaseOrderInput() {
        PurchaseOrderState input = new PurchaseOrderState(partyC.getParty(), partyA.getParty(), "item123");
        PurchaseOrderState output = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item123");
        List<PublicKey> signers = ImmutableList.of(partyA.getPublicKey(), partyB.getPublicKey());

        transaction(ledgerServices, tx -> {
            tx.command(signers, new PurchaseOrderContract.Commands.Issue());
            tx.output(PurchaseOrderContract.ID, output);

            tx.tweak(tw -> {
                tw.input(PurchaseOrderContract.ID, input);
                return tw.fails();
            });

            return tx.verifies();
        });
    }

    /**
     * Check exactly one purchase order in the output
     */
    @Test
    public void includesOnePurchaseOrderOutput() {
        PurchaseOrderState output1 = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item123");
        PurchaseOrderState output2 = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item456");
        List<PublicKey> signers = ImmutableList.of(partyA.getPublicKey(), partyB.getPublicKey());

        transaction(ledgerServices, tx -> {
            tx.command(signers, new PurchaseOrderContract.Commands.Issue());

            // No purchase order in output, fails.
            tx.tweak(tw -> {
                return tw.fails();
            });

            // Too many purchase orders in output, fails.
            tx.tweak(tw -> {
                tw.output(PurchaseOrderContract.ID, output1);
                tw.output(PurchaseOrderContract.ID, output2);
                return tw.fails();
            });

            // One output, verifies.
            tx.output(PurchaseOrderContract.ID, output1);
            return tx.verifies();
        });
    }

    /**
     * Checks buyer and seller are different parties in the output state
     */
    @Test
    public void buyerSellerMustBeDifferent() {
        //Good output: Buyer and seller are different.
        PurchaseOrderState goodOutput = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item123");
        //Bad output: Buyer and seller are identical.
        PurchaseOrderState badOutput = new PurchaseOrderState(partyA.getParty(), partyA.getParty(), "item123");

        List<PublicKey> signers = ImmutableList.of(partyA.getPublicKey(), partyB.getPublicKey());

        transaction(ledgerServices, tx -> {
            tx.command(signers, new PurchaseOrderContract.Commands.Issue());

            tx.tweak(tw -> {
                tw.output(PurchaseOrderContract.ID, badOutput);
                return tw.fails();
            });

            tx.output(PurchaseOrderContract.ID, goodOutput);
            return tx.verifies();
        });
    }

    /**
     * Checks the purchased item ID is specified in output
     */
    @Test
    public void itemIDMustBeSpecified() {
        //Good output: Item ID is specified.
        PurchaseOrderState goodOutput = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item123");
        //Bad output: Item ID is null.
        PurchaseOrderState badOutput = new PurchaseOrderState(partyA.getParty(), partyA.getParty(), null);

        List<PublicKey> signers = ImmutableList.of(partyA.getPublicKey(), partyB.getPublicKey());

        transaction(ledgerServices, tx -> {
            tx.command(signers, new PurchaseOrderContract.Commands.Issue());

            tx.tweak(tw -> {
                tw.output(PurchaseOrderContract.ID, badOutput);
                return tw.fails();
            });

            tx.output(PurchaseOrderContract.ID, goodOutput);
            return tx.verifies();
        });
    }

    /**
     * Checks all expected signers are required in the command.
     */
    @Test
    public void signatureRequiredFromAllParticipants() {
        PurchaseOrderState output = new PurchaseOrderState(partyA.getParty(), partyB.getParty(), "item123");
        List<PublicKey> expectedSigners = ImmutableList.of(partyA.getPublicKey(), partyB.getPublicKey());

        transaction(ledgerServices, tx -> {

            // Not enough signers, fail.
            tx.tweak(tw -> {
                tw.command(ImmutableList.of(partyA.getPublicKey()), new Commands.DummyCommand());
                return tw.fails();
            });

            // Not all participants signed, fail.
            tx.tweak(tw -> {
                tw.command(ImmutableList.of(partyA.getPublicKey(), partyC.getPublicKey()), new Commands.DummyCommand());
                return tw.fails();
            });

            // Not all participants signed, fail.
            tx.tweak(tw -> {
                tw.command(ImmutableList.of(partyB.getPublicKey(), partyC.getPublicKey(), partyD.getPublicKey()), new Commands.DummyCommand());
                return tw.fails();
            });

            tx.command(expectedSigners, new PurchaseOrderContract.Commands.Issue());
            tx.output(PurchaseOrderContract.ID, output);
            return tx.verifies();
        });
    }
}