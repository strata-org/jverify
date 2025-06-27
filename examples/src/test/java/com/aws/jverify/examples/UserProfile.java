package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.Invariant;
import com.aws.jverify.Nullable;
import com.aws.jverify.Erased;
import com.aws.jverify.Pure;

class UserProfile {
    public enum AccountType { Free, Premium }
    public enum Theme { Light, Dark }
    private AccountType accountType;
    private @Nullable PremiumFeatures premiumFeatures;  // Should be non-null for Premium accounts

    public UserProfile(AccountType accountType) {
        this.accountType = accountType;
        if (AccountType.Premium == accountType) {
            this.premiumFeatures = new PremiumFeatures();
        } else {
            this.premiumFeatures = null;
        }
    }

    @Erased
    @Pure
    @Invariant // Makes this a pre- and post-condition of all public methods
    private boolean valid() {
        reads(this);
        return (Object)this != premiumFeatures && 
                (accountType != AccountType.Premium || premiumFeatures != null);
    }

    public void upgradeAccount() {
        modifies(this);
        this.accountType = AccountType.Premium;
        this.premiumFeatures = new PremiumFeatures();
        return;
    }

    public boolean applyTheme(Theme theme) {
        modifies(premiumFeatures);
        if (AccountType.Premium == accountType) {
            // Checker framework will report this as a possible null pointer exception. 
            // JVerify accepts the code
            premiumFeatures.setTheme(theme);
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
