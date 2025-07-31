// verifier


trait Object {

  ghost predicate valid()
    requires Repr.requires()
    reads Repr.reads(), Repr()
    decreases Repr(), 1

  ghost function Repr(): set<object> 
    reads if this is ModifiableObject then {this as ModifiableObject} else {},
          if this is ModifiableObject then (this as ModifiableObject).repr else {}
  {
    if this is ModifiableObject then (this as ModifiableObject).repr else {}
  }

  lemma ReprRequiresTrue()
    ensures Repr.requires()
  {}

  predicate equals(other: Object): (b: bool)
    requires valid()
    requires other.valid()
    reads Repr.reads(), Repr(), other.Repr.reads, other.Repr()
    decreases Repr()
    ensures this as Object == other ==> b

  // lemma equalsSymmetric(other: Object)
  //   requires valid()
  //   requires other.valid()
  //   requires equals(other)
  //   ensures other.equals(this)

  // method equalsImpl(other: Object) returns (b: bool)
  //   requires Repr.requires()
  //   requires valid()
  //   requires other.Repr.requires()
  //   requires other.valid()
  //   ensures b == equals(other)

  function getClass(): Class
}

trait ModifiableObject extends Object, object { 

  var repr: set<object>

  ghost predicate validComponent(component: ModifiableObject)
    reads Repr.reads(), Repr()
    decreases Repr(), 0
  {
      && this in Repr()
      && component in Repr()
      && component.repr <= Repr()
      && this !in component.Repr()
      && component.valid()
  }
}

function JString(s: string): DString
  requires forall i | 0 <= i < |s| :: 0 <= s[i] as int <= 65535
{
  JS(seq(|s|, i requires 0 <= i < |s| => s[i] as char16))
}

trait MyPair extends ModifiableObject, object {
  var a: A
  var b: B

  method init??_MyPair(a: A, b: B)
    modifies this
  {
    this.a := a;
    this.b := b;
  }

  ghost predicate valid() 
    requires Repr.requires()
    reads Repr.reads(), Repr()
    decreases Repr(), 1
  {
    && this in Repr()
    && validComponent(a)
    && validComponent(b)
  }

  predicate equals(obj: Object): (b: bool)
    requires valid()
    requires obj.valid()
    reads Repr.reads, Repr(), obj.Repr.reads, obj.Repr()
    ensures this as Object == obj ==> b
    decreases Repr()
  {
    if !(obj is MyPair) then
      false
    else
      var other: MyPair := obj as MyPair;
      assert validComponent(a);
      // assert other in (set _obj: object? /*{:_reads}*/ {:trigger Repr.reads in Repr.reads()} | Repr.reads in Repr.reads() :: Repr.reads) || other in Repr() || other in (set _obj: object? /*{:_reads}*/ {:trigger obj.Repr.reads in obj.Repr.reads()} | obj.Repr.reads in obj.Repr.reads() :: obj.Repr.reads) || other in obj.Repr();
      var result? := a.equals(other.a) && b.equals(other.b);
      result?
  }

  // method equalsImpl(obj: Object) returns (result?: bool)
  //   requires Repr.requires()
    
  //   requires valid()
  //   requires obj.valid()
  //   ensures result? == equals(obj)
  // {
  //   if !(obj is MyPair) {
  //     result? := false;
  //     return;
  //   }
  //   var other: MyPair := obj as MyPair;
  //   result? := a.equals(other.a) && b.equals(other.b);
  //   return;
  // }
}

class Constructable?MyPair extends MyPair {

  constructor ctor?(a: A, b: B)

  // predicate equals(other: Object): (b: bool)
  //   ensures this as Object == other ==> b

  // lemma equalsSymmetric(other: Object)
  //   requires valid()
  //   requires other.valid()
  //   requires equals(other)
  //   ensures other.equals(this)
  // {}

  function getClass(): Class
}

trait A extends ModifiableObject {
  var f: int32

  method init??_A(f: int32)
    modifies this
  {
    this.f := f;
  }

  // method equalsImpl(obj: Object) returns (result?: bool)
  //   ensures result? == equals(obj)
  // {
  //   if !(obj is A) {
  //     result? := false;
  //     return;
  //   }
  //   var other: A := obj as A;
  //   result? := f == other.f;
  //   return;
  // }
}

class Constructable?A extends A {

  ghost predicate valid()
    requires Repr.requires()
    reads Repr.reads(), Repr()
    decreases Repr(), 0

  constructor ctor?(f: int32)

  predicate equals(other: Object): (b: bool)
    ensures this as Object == other ==> b
    decreases Repr()

  // lemma equalsSymmetric(other: Object)
  //   requires equals(other)
  //   ensures other.equals(this)
  // {}

  function getClass(): Class
}

trait B extends A, object {
  var g: int32

  method init??_B(f: int32, g: int32)
    modifies this
  {
    init??_A(f);
    this.g := g;
  }

  // method equalsImpl(obj: Object) returns (result?: bool)
  //   ensures result? == equals(obj)
  // {
  //   if !(obj is B) {
  //     result? := false;
  //     return;
  //   }
  //   var other: B := obj as B;
  //   result? := f == other.f && g == other.g;
  //   return;
  // }
}

class Constructable?B extends B {
  ghost predicate valid()
    requires Repr.requires()
    reads Repr.reads(), Repr()
    decreases Repr(), 1

  constructor ctor?(f: int32, g: int32)

  function equals(other: Object): (b: bool)
    decreases Repr()
    ensures this as Object == other ==> b


  // lemma equalsSymmetric(other: Object)
  //   requires equals(other)
  //   ensures other.equals(this)
  // {}

  function getClass(): Class
}

class Constructable?ModifiableObject extends ModifiableObject {

  ghost predicate valid()
    requires Repr.requires()
    reads Repr.reads(), Repr()
    decreases Repr(), 1

  constructor ctor?()

  predicate equals(other: Object): (b: bool)
    decreases Repr()
    ensures this as Object == other ==> b
  {
    other is ModifiableObject && this == other as ModifiableObject
  }


  // lemma equalsSymmetric(other: Object)
  //   requires equals(other)
  //   ensures other.equals(this)
  // {}

  function getClass(): Class

  // method equalsImpl(other: Object) returns (b: bool)
  //   ensures b == equals(other)
}

trait Class extends ModifiableObject {

}

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

type char16 = i: int
  | 0 <= i <= 65535

datatype DString extends Object = JS(elements: seq<char16>) {
  ghost predicate valid()
    requires Repr.requires()
    reads Repr.reads(), Repr()
    decreases Repr(), 1

  function indexOf_i(c: char16): (result: int32)
    ensures -1 <= result < |this.elements|
    ensures result == -1 <==> forall i | 0 <= i < |this.elements| :: this.elements[i] != c
    ensures result >= 0 && result < |this.elements| ==> this.elements[result] == c && forall j | 0 <= j && j < result :: this.elements[j] != c

  function length(): int
  {
    |this.elements|
  }

  function charAt(x: int): char16
    requires 0 <= x < |this.elements|
  {
    this.elements[x]
  }

  function isEmpty(): bool
  {
    this.elements == []
  }

  function concat(other: DString): DString
  {
    JS(this.elements + other.elements)
  }

  function substring_i(start: int): DString
    requires start >= 0 && start <= |this.elements|
  {
    JS(this.elements[start..])
  }

  function substring_ii(start: int, end: int): DString
    requires start >= 0 && start <= end && end <= |this.elements|
  {
    JS(this.elements[start .. end])
  }

  predicate equals(other: Object): (b: bool)
    decreases Repr()
    ensures this as Object == other ==> b
  {
    other is DString &&
    this.elements == (other as DString).elements
  }

  // lemma equalsSymmetric(other: Object)
  //   requires equals(other)
  //   ensures other.equals(this)
  // {}


  function startsWith_Cjava_lang_String(other: DString): (b: bool)
    ensures |other.elements| > |this.elements| ==> b == false
    ensures |other.elements| <= |this.elements| && (forall i | 0 <= i < |other.elements| :: other.elements[i] == this.elements[i]) ==> b == true
    ensures |other.elements| <= |this.elements| && (exists i | 0 <= i < |other.elements| :: other.elements[i] != this.elements[i]) ==> b == false

  function getClass(): Class

  // method equalsImpl(other: Object) returns (b: bool)
  //   ensures b == equals(other)
}

datatype DNull extends Object = DNull {
  ghost predicate valid()

  predicate equals(other: Object): (b: bool)
    decreases Repr()
    ensures this as Object == other ==> b
  {
    other is DNull
  }

  function getClass(): Class
  // method equalsImpl(other: Object) returns (b: bool)
  //   ensures b == equals(other)

  // lemma equalsSymmetric(other: Object)
  //   requires equals(other)
  //   ensures other.equals(this)
  // {}
}

type byte = x
  | 0 <= x < 256

type float = real

type double = real


trait List<T> extends Object, object {

  ghost const elements: seq<T>

  predicate equals(obj: Object): (b: bool)
    decreases Repr()
    ensures this as Object == obj ==> b
    ensures b <==>
      && obj is List<T> 
      && var other := obj as List<T>;
      && elements == other.elements
}