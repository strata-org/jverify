# Class Invariants
Here follows a program that contains a `NullReferenceException`. The class `UserProfile` can be either `Free` or `Premium`, and for performance reasons, the field `premiumFeatures` should only be set when the account is `Premium`. The program:

```java
package org.strata.jverify.examples;

import static org.strata.jverify.JVerify.*;
import org.strata.jverify.Invariant;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;

class UserProfile {
    public enum AccountType { Free, Premium }
    public enum Theme { Light, Dark }
    private AccountType accountType;
    private PremiumFeatures premiumFeatures;  // For performance reasons, 
    // only assign this when the account type is premium

    public UserProfile(AccountType accountType) {
        this.accountType = accountType;
        if (AccountType.Premium == accountType) {
            this.premiumFeatures = new PremiumFeatures();
        }
    }

    public void upgradeAccount() {
        modifies(this);
        this.accountType = AccountType.Premium;
    }

    public boolean applyTheme(Theme theme) {
        modifies(premiumFeatures);
        if (AccountType.Premium == accountType) {
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
```

Running JVerify on this produces:
```
error: non-nullable field `premiumFeatures` not assigned at end of constructor
  |   this.premiumFeatures = new PremiumFeatures();
  | }
  | ^
```

To resolve the above error, we need to let JVerify know that the field `premiumFeatures` is allowed to be null, by using the `@Nullable` annotation:
```java
private @Nullable PremiumFeatures premiumFeatures;
```

Running JVerify again we now get:
```
error: target object might be null 
  |  premiumFeatures.setTheme(theme);
  |  ^^^^^^^^^^^^^^^
```

We have not specified in our code, that when `accountType` is `AccountType.Premium`, `premiumFeatures` must not be null, so let's do that using a new concept called a class invariant. We'll add the following code:

```java
@Invariant
private boolean valid() {
    reads(this);
    return accountType != AccountType.Premium || premiumFeatures != null;
}
```

Running JVerify again, we get:
```
error: invariant could not be proven on this return path 
  | public void upgradeAccount() {
  |   modifies(this);
  |   this.accountType = AccountType.Premium;
  |   return;
      ^^^^^^
```

JVerify has told us where our bug is. We can now fix it by adding the line
```java
this.premiumFeatures = new PremiumFeatures();
```
to `upgradeAccount`. When we rerun JVerify, it reports no errors.