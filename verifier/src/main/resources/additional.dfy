type nat15 = x: int16 | x >= 0
type int16 = x: int | -0x8000 <= x <= 0x7fff

type nat31 = x: int32 | x >= 0
type int32 = x: int | -0x8000_0000 <= x && x <= 0x7fff_ffff
  
type nat63 = x: int64 | x >= 0
type int64 = x: int | -0x8000_0000_0000_0000 <= x <= 0x7fff_ffff_ffff_ffff

// Base type is int and not char, because Java's char allows surrogates and Dafny's char does not
type char16 = i: int | 0x0000 <= i <= 0xffff
type jstring = seq<char16>

function JString(s: string): jstring
  requires forall i | 0 <= i < |s| :: 0x0000 <= s[i] as int <= 0xffff
{
  seq(|s|, i requires 0 <= i < |s| => s[i] as char16)
}

type byte = x | 0 <= x < 256

type float = real
type double = real