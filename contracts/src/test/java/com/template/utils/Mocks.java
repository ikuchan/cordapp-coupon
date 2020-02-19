package com.template.utils;

import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;

public class Mocks {

    public static final TestIdentity partyA = new TestIdentity(new CordaX500Name("PartyA", "", "JP"));
    public static final TestIdentity partyB = new TestIdentity(new CordaX500Name("PartyB", "", "JP"));
    public static final TestIdentity partyC = new TestIdentity(new CordaX500Name("PartyC", "", "JP"));
    public static final TestIdentity partyD = new TestIdentity(new CordaX500Name("PartyD", "", "JP"));
    public static final TestIdentity partyE = new TestIdentity(new CordaX500Name("PartyE", "", "JP"));
    public static final TestIdentity megaBank = new TestIdentity(new CordaX500Name("MegaBank", "", "JP"));
}
