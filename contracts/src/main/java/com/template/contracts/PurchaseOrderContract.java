package com.template.contracts;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.template.states.PurchaseOrderState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

public class PurchaseOrderContract implements Contract {
    public static String ID = "com.template.contracts.PurchaseOrderContract";

    public interface Commands extends CommandData {
        class Issue implements Commands {}
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        // Ensures the transaction has exactly one purchase order command
        CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        CommandData commandData = command.getValue();
        String commandName = commandData.getClass().getSimpleName();

        if (commandData instanceof Commands.Issue) {
            /* Shape constraints */

            // Verifies there is no PurchaseOrder in the input
            List<PurchaseOrderState> unwantedInputStates = tx.inputsOfType(PurchaseOrderState.class);
            if (unwantedInputStates.size() > 0) {
                throw new IllegalArgumentException(String.format("%s must not have purchase order as input.", commandName));
            }

            // Verifies there is exactly one PurchaseOrder in the output
            List<PurchaseOrderState> desiredOutputStates = tx.outputsOfType(PurchaseOrderState.class);
            if (desiredOutputStates.size() != 1) {
                throw new IllegalArgumentException(String.format("%s must have exactly one purchase order as output.", commandName));
            }

            PurchaseOrderState purchaseOrder = desiredOutputStates.get(0);

            /* Content constraints */

            // Verifies the buyer and the seller are different
            if (purchaseOrder.getBuyer().getOwningKey().equals(purchaseOrder.getSeller().getOwningKey())) {
                throw new IllegalArgumentException(String.format("Seller and buyer must not be the same party in %s", commandName));
            }

            // Verifies the item ID is specified in the PurchaseOrder
            if (purchaseOrder.getItemId() == null) {
                throw new IllegalArgumentException(String.format("Item ID must be set in %s", commandName));
            }

            /* Signer constraints */

            final List<PublicKey> requiredSigners = command.getSigners();
            final List<PublicKey> expectedSigners = purchaseOrder.getParticipants()
                    .stream().map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());

            // Verifies right number of signers are required in the command
            if ( requiredSigners.size() < expectedSigners.size()) {
                throw new IllegalArgumentException(String.format("%s requires exactly %d signers.", commandName, expectedSigners.size()));
            }

            // Verifies required signers covers all participants in the purchase order
            if( !requiredSigners.containsAll(expectedSigners) ) {
                throw new IllegalArgumentException(String.format("%s requires signatures from all contract participants."));
            }

        } else {
            throw new IllegalArgumentException("Command not supported.");
        }

    }
}
