package com.aws.jverify;

class UserProfile {
    public enum AccountType { Free, Premium }
    public enum Theme { Light, Dark }
    private AccountType accountType;
    private @Nullable PremiumFeatures premiumFeatures;  // Should be null for FREE accounts

    public UserProfile(AccountType accountType) {
        this.accountType = accountType;
        if (AccountType.Premium == accountType) {
            this.premiumFeatures = new PremiumFeatures();
        }
    }
    
    @Pure
    @Invariant // Makes this a pre- and post-condition of all public methods
    private boolean valid() {
        return accountType != AccountType.Premium || premiumFeatures != null;
    }
    public void upgradeAccount() {
        this.accountType = AccountType.Premium;
        // JVerify error: could not prove "premiumFeatures != null"
    }
    public boolean applyTheme(Theme theme) {
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
            this.theme = theme;
        }
    
        public Theme getTheme() {
            return theme;
        }
    }
}
