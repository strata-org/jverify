package org.strata.jverify.examples;

import static org.strata.jverify.JVerify.*;

import org.strata.jverify.*;

class UserProfile {
    public enum AccountType { Free, Premium }
    public enum Theme { Light, Dark }
    private AccountType accountType;
    private [>@Nullable <]PremiumFeatures premiumFeatures;  // Should be non-null for Premium accounts

    public UserProfile(AccountType accountType) {
        this.accountType = accountType;
        if (AccountType.Premium == accountType) {
            this.premiumFeatures = new PremiumFeatures();
        } else {
            this.premiumFeatures = null;
//                                 ^^^^ Error: value of expression (of type 'PremiumFeatures?') is not known to be an instance of type 'PremiumFeatures', because it could not be proved to be non-null
        }
    }[>
    
    @Erased
    @Pure
    @Invariant // Makes this a pre- and post-condition of all public methods
    private boolean valid() {
//                  ^^^^^ Related location: this is the postcondition that could not be proved
        reads(this);
        return (@Impure Object)this != premiumFeatures &&
                (accountType != AccountType.Premium || premiumFeatures != null);
//               ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Related location: this proposition could not be proved
    }
    <]

    public void upgradeAccount() {
        modifies(this);
        this.accountType = AccountType.Premium;[>
        this.premiumFeatures = new PremiumFeatures();<]
        return;
//      ^^^^^^^ Error: a postcondition could not be proved on this return path
    }

    public boolean applyTheme(Theme theme) {
        modifies(premiumFeatures);
        if (AccountType.Premium == accountType) {
            // Checker framework will report this as a possible null pointer exception. 
            // JVerify accepts the code
            premiumFeatures.setTheme(theme);
//          ^^^^^^^^^^^^^^^ Error: target object could not be proved to be non-null
            return true;
        } else {
            return false;
        }
    }

    static class PremiumFeatures {
        private Theme theme;

        public void setTheme(Theme theme) {
            modifies(this);
            this.theme = theme;
        }

        public Theme getTheme() {
            return theme;
        }
    }
}
