package com.template.contract;

import com.template.states.PurchaseOrderState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

public class PurchaseOrderContract implements Contract {
    public static String ID = "com.template.contract.PurchaseOrderContract";

    public interface Commands extends CommandData {
        class Purchase implements Commands {}
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        if (tx.getCommands().size() > 1) {
            throw new IllegalArgumentException("Transaction must have exactly one command.");
        }

        CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        CommandData commandData = command.getValue();
        String commandName = commandData.getClass().getSimpleName();

        if (commandData instanceof Commands.Purchase) {
            // Shape constraints
            if (tx.getInputs().size() < 1) {
                throw new IllegalArgumentException(String.format("%s must have at least one input.", commandName));
            }

            if (tx.getOutputs().size() != 1) {
                throw new IllegalArgumentException(String.format("%s must have exactly one output.", commandName));
            }

            // Content constraints
            PurchaseOrderState output = tx.outputsOfType(PurchaseOrderState.class).get(0);
            if (output.getBuyer().getOwningKey().equals(output.getSeller().getOwningKey())) {
                throw new IllegalArgumentException(String.format("Seller and buyer must not be the same party in %s", commandName));
            }

            if (output.getItemId() == null) {
                throw new IllegalArgumentException(String.format("Item ID must be set in %s", commandName));
            }

            // Signer constraints
            final List<PublicKey> requiredSigners = command.getSigners();
            final List<PublicKey> expectedSigners = output.getParticipants()
                    .stream().map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());

            if ( requiredSigners.size() < expectedSigners.size()) {
                throw new IllegalArgumentException(String.format("%s requires exactly %d signers.", commandName, expectedSigners.size()));
            }

            if( !requiredSigners.containsAll(expectedSigners) ) {
                throw new IllegalArgumentException(String.format("%s requires signatures from all contract participants."));
            }

        } else {
            throw new IllegalArgumentException("Command not supported.");
        }

    }
}
