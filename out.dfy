// additional.dfy

type nat32 = x: int32
  | x >= 0

type int32 = x: int
  | -2147483647 <= x && x <= 2147483647

class UserProfile {
  var accountType: AccountType
  var premiumFeatures: PremiumFeatures?

  constructor (accountType: AccountType)
  {
    this.accountType := accountType;
    if Premium() == accountType {
      this.premiumFeatures := new PremiumFeatures();
    }
  }

  function valid(): bool
    reads this
  {
    accountType != Premium() || premiumFeatures != null
  }

  method upgradeAccount()
    modifies this
  {
    this.accountType := Premium();
  }

  method applyTheme(theme: Theme) returns (r: bool)
    modifies premiumFeatures
  {
    if Premium() == accountType {
      premiumFeatures.setTheme(theme);
      return true;
    } else {
      return false;
    }
  }
}

class PremiumFeatures {
  constructor ()
  {
  }

  var theme: Theme

  method setTheme(theme: Theme)
    modifies this
  {
    this.theme := theme;
  }

  method getTheme() returns (r: Theme)
  {
    return theme;
  }
}

datatype Theme = Light | Dark

datatype AccountType = Free | Premium
