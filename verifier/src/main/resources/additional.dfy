trait Object {
    // This should have a non-empty reads clause,
    // but that will require something like the
    // Valid()/Repr idiom extended to allow non-reference types.
    // For now we only support equality definitions
    // that do not depend on mutable state.
    //
    // See also JavaToDafnyCompiler.equalsFunctionDeclaration
    // for more on overridding pure methods.
    predicate equals(obj: Object)
}

type nat15 = x: int16 | x >= 0
type int16 = x: int | -0x8000 <= x <= 0x7fff

type nat31 = x: int32 | x >= 0
type int32 = x: int | -0x8000_0000 <= x && x <= 0x7fff_ffff
  
type nat63 = x: int64 | x >= 0
type int64 = x: int | -0x8000_0000_0000_0000 <= x <= 0x7fff_ffff_ffff_ffff

// Base type is int and not char, because Java's char allows surrogates and Dafny's char does not
type char16 = i: int | 0x0000 <= i <= 0xffff
datatype DString extends Object = JS(elements: seq<char16>) {
    function indexOf_i(c: char16) : (result:int32)
        ensures -1 <= result < |this.elements|
        ensures (result == -1 <==> forall i | 0 <= i < |this.elements| :: this.elements[i] != c)
        ensures (result >= 0 && result<|this.elements| ==> this.elements[result] == c &&   forall j| 0 <= j && j < result :: this.elements[j] != c)


     function length() : int {
        |this.elements|
        }
      function charAt(x: int) : char16
        requires 0<=x<|this.elements|
      {
        this.elements[x]
      }
      function isEmpty() : bool {
        this.elements == []
      }
      function concat(other: DString) : DString {
        JS(this.elements + other.elements)
      }
    function substring_i(start: int) : DString
      requires start >= 0 && start <= |this.elements|
    {
      JS(this.elements[start..])
    }
    function substring_ii(start: int, end:int) : DString
        requires start >= 0 && start <= end && end <= |this.elements|
    {
      JS(this.elements[start..end])
    }
    function equals(other: Object) : (b: bool)
    {
      other is DString && this.elements == (other as DString).elements
    }

    function startsWith_Cjava_lang_String(other : DString) : (b : bool)
        ensures( |other.elements| > |this.elements| ==> b==false)
        ensures( |other.elements| <= |this.elements| && (forall i | 0 <= i < |other.elements| :: other.elements[i] == this.elements[i]) ==> b == true)
        ensures( |other.elements| <= |this.elements| && (exists i | 0 <= i < |other.elements| :: other.elements[i] != this.elements[i]) ==> b == false)

}

function JString(s: string): DString
  requires forall i | 0 <= i < |s| :: 0x0000 <= s[i] as int <= 0xffff
{
  JS(seq(|s|, i requires 0 <= i < |s| => s[i] as char16))
}


type byte = x | 0 <= x < 256

type float = real
type double = real