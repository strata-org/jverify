package com.aws.verifier.examples;

import com.aws.jverify.Invariant;
import com.aws.jverify.Nullable;
import com.aws.jverify.Pure;

class VerifiedUserProfile {
    public enum AccountType { Free, Premium }
    private AccountType accountType;
    private @Nullable PremiumFeatures premiumFeatures;  // Should be null for FREE accounts

    public VerifiedUserProfile(AccountType accountType) {
        this.accountType = accountType;
        if (AccountType.Premium.equals(accountType)) {
            this.premiumFeatures = new PremiumFeatures();
        }
    }
    @Pure
    @Invariant // Makes this a pre- and post-condition of all public methods
    private boolean valid() {
        return !accountType.equals(AccountType.Premium) || premiumFeatures != null;
    }
    public void upgradeAccount() {
        this.accountType = AccountType.Premium;
        // JVerify error: could not prove "premiumFeatures != null"
    }
    public String applyTheme(String theme) {
        if (AccountType.Premium.equals(accountType)) {
            // Checker framework will report this as a possible null pointer exception. 
            // JVerify accepts the code
            premiumFeatures.setTheme(theme);
            return "Theme applied: " + theme;
        } else {
            return "Themes are only available for premium accounts";
        }
    }
}


class UserProfile {
    private String userId;
    private String accountType;  // "FREE" or "PREMIUM"
    private @Nullable PremiumFeatures premiumFeatures;  // Should be null for FREE accounts

    public UserProfile(String userId, String accountType) {
        this.userId = userId;
        this.accountType = accountType;

        if ("PREMIUM".equals(accountType)) {
            this.premiumFeatures = new PremiumFeatures();
        }
    }

    public void upgradeAccount() {
        this.accountType = "PREMIUM";
        // Bug: We forgot to initialize premiumFeatures when upgrading
    }

    public String applyTheme(String theme) {
        if ("PREMIUM".equals(accountType)) {
            // Checker framework will complain this may cause a null pointer exception
            premiumFeatures.setTheme(theme);
            return "Theme applied: " + theme;
        } else {
            return "Themes are only available for premium accounts";
        }
    }
}

class PremiumFeatures {
    private String theme = "Default";
    private boolean customExports = false;

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getTheme() {
        return theme;
    }

    public void setCustomExports(boolean enabled) {
        this.customExports = enabled;
    }

    public boolean hasCustomExports() {
        return customExports;
    }
}
