package com.template.flows;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.template.states.CouponTokenType;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;

import java.util.ArrayList;
import java.util.List;

@StartableByRPC
public class PrintStates extends FlowLogic<String> {
    @Override
    public String call() throws FlowException {
        List<StateAndRef<ContractState>> allStatesAndRefs = getServiceHub().getVaultService().queryBy(ContractState.class).getStates();

        ArrayList<StateAndRef<ContractState>> cashStates = new ArrayList<StateAndRef<ContractState>>();
        ArrayList<StateAndRef<ContractState>> couponStates = new ArrayList<StateAndRef<ContractState>>();
        ArrayList<StateAndRef<ContractState>> purchaseOrderStates = new ArrayList<StateAndRef<ContractState>>();

        StringBuffer output= new StringBuffer("\n\n");
        allStatesAndRefs.forEach(state -> {
            if (state.getState().getData() instanceof NonFungibleToken) {
                // Because we queried for NonFungibleToken
                NonFungibleToken nonFungibleTokenState = (NonFungibleToken) state.getState().getData();

                // Because we wrapped the token type with an IssuedTokenType
                TokenType realTokenType = nonFungibleTokenState.getIssuedTokenType().getTokenType();

                if (realTokenType instanceof CouponTokenType) {
                    // CouponTokenType c = (CouponTokenType) realTokenType;
                    // output.append("***** Coupon: " + c.getDiscount() + "% off ****\n");
                    couponStates.add(state);
                }
            }
            else if (state.getState().getData() instanceof FungibleToken) {
                cashStates.add(state);
            }
            else {
                // output.append("**** State ****\n");
                purchaseOrderStates.add(state);
            }

            // output.append(state.getState().getData().toString() + "\n\n");
        });

        output.append("***** CASH STATES ****\n");
        cashStates.forEach(stateAndRef -> {
            output.append(stateAndRef.getState().getData().toString() + "\n\n");
        });

        output.append("***** COUPON STATES ****\n");
        couponStates.forEach(stateAndRef -> {
            output.append(stateAndRef.getState().getData().toString() + "\n\n");
        });

        output.append("***** PURCHASE ORDER STATES ****\n");
        purchaseOrderStates.forEach(stateAndRef -> {
            output.append(stateAndRef.getState().getData().toString() + "\n\n");
        });

        return output.toString();
    }
}
