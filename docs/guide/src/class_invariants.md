# Class Invariants
Here follows a program that contains an NRE. The class `UserProfile` can be either free or premium, and for performance reasons, the field `premiumFeatures` should only be set when the account is premium. The program:

```java
package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.Invariant;
import com.aws.jverify.Nullable;
import com.aws.jverify.Pure;

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

So let's make the field `premiumFatures` nullable, using the `@Nullable` annotation:
```java
private @Nullable PremiumFeatures premiumFeatures;
```

Running JVerify again we now get:
```
error: target object might be null 
  |  premiumFeatures.setTheme(theme);
  |  ^^^^^^^^^^^^^^^
```

JVerify does not know that if `accountType` is `AccountType.Premium`, `premiumFeatures` will always be set, so let's add something called an `invariant` to let it know:

```java
@Invariant
private boolean valid() {
    reads(this);
    return accountType != AccountType.Premium || premiumFeatures != null;
}
```