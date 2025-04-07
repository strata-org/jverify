// additional.dfy

type nat15 = x: int16
  | x >= 0

type int16 = x: int
  | -32768 <= x <= 32767

type nat31 = x: int32
  | x >= 0

type int32 = x: int
  | -2147483648 <= x && x <= 2147483647

type nat63 = x: int64
  | x >= 0

type int64 = x: int
  | -9223372036854775808 <= x <= 9223372036854775807

type char16 = c: char
  | '\U{0000}' <= c <= '\U{ffff}'

type byte = x
  | 0 <= x < 256

type Float(!new)

type Double(!new)

class Types {
  constructor ()
  {
  }

  method foo(s: int32)
  {
    var x: int16 := 10;
    var y: int32 := 20;
    var z: int64 := 30;
    if s == 0 {
      var a: nat15 := -10;
    } else {
      if s == 1 {
        var b: nat31 := -20;
      } else {
        if s == 2 {
          var c: nat63 := -30;
        }
      }
    }
    var c: char16 := 'q';
    var b: byte := 10;
    var f: Float;
    var d: Double;
  }
}

class Long {
  constructor ()

  static const {:verify false} MIN_VALUE: int64 := -9223372036854775808
  static const {:verify false} MAX_VALUE: int64 := 9223372036854775807
}

class Integer {
  constructor ()

  static const {:verify false} MAX_VALUE: int32 := 2147483647
  static const {:verify false} MIN_VALUE: int32 := -2147483648
}

class Short {
  constructor ()

  static const {:verify false} MIN_VALUE: int16 := -32768
  static const {:verify false} MAX_VALUE: int16 := 32767
}
