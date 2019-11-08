package com.template.states;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@BelongsToContract(PurchaseOrderContract.class)
public class PurchaseOrderState implements ContractState {
    private final Party seller;
    private final Party buyer;
    private final String itemId;

    public PurchaseOrderState(Party seller, Party buyer, String itemId) {
        this.seller = seller;
        this.buyer = buyer;
        this.itemId = itemId;
    }

    public Party getSeller() {
        return seller;
    }

    public Party getBuyer() {
        return buyer;
    }

    public String getItemId() {
        return itemId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(seller, buyer);
    }

    @Override
    public String toString() {
        return "Purchase Order for item " + itemId + " from Seller " + seller.getName() + " to Buyer " + buyer.getName();
    }
}
