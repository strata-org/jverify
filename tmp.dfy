// additional.dfy

type nat32 = x: int32
  | x >= 0

type int32 = x: int
  | -2147483647 <= x && x <= 2147483647

class Operators {
  constructor ()
  {
  }

  static method Plus()
  {
    var x: int32 := 3;
    var y: int32 := 4;
    var z: int32 := x + y;
    assert z == 7;
  }

  static method PlusKO()
  {
    var x: int32 := 3;
    var y: int32 := 4;
    var z: int32 := x + y;
    assert z == 6;
  }

  static method Minus()
  {
    var x: int32 := 3;
    var y: int32 := 4;
    var z: int32 := y - x;
    assert z == 1;
  }

  static method MinusKO()
  {
    var x: int32 := 3;
    var y: int32 := 4;
    var z: int32 := y - x;
    assert z == 0;
  }

  static method Mult()
  {
    var x: int32 := 3;
    var y: int32 := 4;
    var z: int32 := x * y;
    assert z == 12;
  }

  static method MultKO()
  {
    var x: int32 := 3;
    var y: int32 := 4;
    var z: int32 := x * y;
    assert z == 11;
  }

  static method Mod()
  {
    var x: int32 := 3;
    var y: int32 := 23;
    var z: int32 := y % x;
    assert z == 2;
  }

  static method ModKO()
  {
    var x: int32 := 3;
    var y: int32 := 23;
    var z: int32 := y % x;
    assert z == 1;
  }

  static method Gt()
  {
    var x: int32 := 3;
    var y: int32 := 23;
    assert y > x;
  }

  static method GtKO()
  {
    var x: int32 := 33;
    var y: int32 := 23;
    assert y > x;
  }

  static method Lt()
  {
    var x: int32 := 3;
    var y: int32 := 23;
    assert x < y;
  }

  static method LtKO()
  {
    var x: int32 := 33;
    var y: int32 := 23;
    assert x < y;
  }

  static method Leq()
  {
    var x: int32 := 3;
    var y: int32 := 23;
    assert x <= y;
  }

  static method LeqKO()
  {
    var x: int32 := 33;
    var y: int32 := 23;
    assert x <= y;
  }

  static method Geq()
  {
    var x: int32 := 3;
    var y: int32 := 23;
    assert y >= x;
  }

  static method GeqKO()
  {
    var x: int32 := 33;
    var y: int32 := 23;
    assert y >= x;
  }

  static method Eq()
  {
    var x: int32 := 3;
    var y: int32 := 3;
    assert x == y;
  }

  static method EqKO()
  {
    var x: int32 := 4;
    var y: int32 := 3;
    assert x == y;
  }
}

class Integer {
  constructor ()

  static const {:verify false} MAX_VALUE: int32 := 2147483647
}
