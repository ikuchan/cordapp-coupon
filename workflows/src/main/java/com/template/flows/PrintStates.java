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

import java.util.List;

@StartableByRPC
public class PrintStates extends FlowLogic<String> {
    @Override
    public String call() throws FlowException {
        List<StateAndRef<ContractState>> allStatesAndRefs = getServiceHub().getVaultService().queryBy(ContractState.class).getStates();

        StringBuffer output= new StringBuffer("\n\n");
        allStatesAndRefs.forEach(state -> {

            if (state.getState().getData() instanceof NonFungibleToken) {
                // Because we queried for NonFungibleToken
                NonFungibleToken nonFungibleTokenState = (NonFungibleToken) state.getState().getData();

                // Because we wrapped the token type with an IssuedTokenType
                TokenType realTokenType = nonFungibleTokenState.getIssuedTokenType().getTokenType();

                if (realTokenType instanceof CouponTokenType) {
                    CouponTokenType c = (CouponTokenType) realTokenType;
                    System.out.println("Item Id is " + c.getItemId());
                    output.append("***** Coupon: " + c.getItemId() + "****\n");
                }
                else if (realTokenType.isRegularTokenType()) {
                    output.append("***** Regular Non-Fungible Token ****\n");
                }
            } else {
                output.append("**** Boring Token ****\n");
            }

            output.append(state.getState().getData().toString() + "\n\n");
        });

        return output.toString();
    }
}
