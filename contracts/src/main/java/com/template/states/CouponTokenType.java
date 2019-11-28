package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.jetbrains.annotations.NotNull;

public class CouponTokenType extends TokenType {
    public static final String IDENTIFIER = "CouponToken";

    private final String itemId;

    private final int discount;

    public CouponTokenType(@NotNull String itemId, int discount) {
        super(IDENTIFIER, 0);
        this.itemId = itemId;
        this.discount = discount;
    }

    public String getItemId() {
        return itemId;
    }

    public int getDiscount() {  return discount; }

    @NotNull
    @Override
    public String toString() {
        return super.toString() + " " + discount +  "% off discount";
    }
}
