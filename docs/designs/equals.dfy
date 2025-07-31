//
// Supertype of all Java types
// Equivalent of java.lang.Object
//
trait Object {

  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  // Generalized "this" in the Valid()/Repr idiom,
  // allowing for value types as well.
  ghost function This(): set<object> {
    if this is ModifiableObject then
      {this as ModifiableObject}
    else
      {}
  }

  ghost function Repr(): set<object> 
    reads This()
  {
    if this is ModifiableObject then
     (this as ModifiableObject).repr
    else
     {}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    decreases Repr()
    // Reflexivity
    ensures this as Object == obj ==> equals(obj)

  // Symmetry
  // Can't be an intrinsic postcondition of equals
  // because we can't quantify over reference types.
  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    ensures obj.equals(this)

  // Will also need a lemma for transitivity
}

//
// Supertype of all Java types mapped to Dafny reference types
//
trait ModifiableObject extends Object, object { 

  // Can't be declared on Object (unless it's a const instead)
  var repr: set<object>

  ghost predicate validComponent(component: ModifiableObject)
    reads Repr.reads(), Repr()
    decreases Repr(), 0
  {
      && this in Repr()
      && component in Repr()
      && component.Repr() <= Repr()
      && this !in component.Repr()
      && component.valid()
  }
}

//
// Example reference type
//
trait MyPair extends ModifiableObject, object {
  var a: A
  var b: B

  ghost predicate valid() 
    reads This(), Repr()
    decreases Repr(), 1
  {
    && this in Repr()
    && validComponent(a)
    && validComponent(b)
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    ensures this as Object == obj ==> equals(obj)
    decreases Repr()
  {
    if !(obj is MyPair) then
      false
    else
      var other: MyPair := obj as MyPair;

      assert validComponent(a);
      assert other.validComponent(other.a);
      assert validComponent(b);
      assert other.validComponent(other.b);
      
      a.equals(other.a) && b.equals(other.b)
  }

  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    ensures obj.equals(this)
  {
    // TODO
  }
}

type int32 = x: int
  | -2147483648 <= x && x <= 2147483647

trait A extends ModifiableObject {
  var f: int32
}

class Constructable?A extends A {

  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 0

  predicate equals(obj: Object)
    ensures this as Object == obj ==> equals(obj)
    decreases Repr()

  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    ensures obj.equals(this)
  {
    // TODO
  }
}

trait B extends A, object {
  var g: int32
}

class Constructable?B extends B {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  function equals(obj: Object): (b: bool)
    decreases Repr()
    ensures (this as Object == obj) ==> equals(obj)


  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    ensures obj.equals(this)
  {
    // TODO
  }
}

class Constructable?ModifiableObject extends ModifiableObject {

  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
  {
    obj is ModifiableObject && this == obj as ModifiableObject
  }


  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    ensures obj.equals(this)
  {
    assert obj as Object == this;
  }
}


type char16 = i: int
  | 0 <= i <= 65535

//
// Equivalent of java.lang.String
//
datatype DString extends Object = JS(elements: seq<char16>) {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  predicate equals(other: Object): (b: bool)
    decreases Repr()
    ensures this as Object == other ==> b
  {
    && other is DString
    && this.elements == (other as DString).elements
  }

  lemma equalsSymmetric(other: Object)
    requires valid()
    requires other.valid()
    requires equals(other)
    ensures other.equals(this)
  {}
}

//
// Example value type
//
// Would be expressed in Java using record types
// or classes with the JVerify @Immutable annotation.
//
datatype DList extends Object = Cons(head: int32, tail: DList) | Nil {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  predicate equals(other: Object): (b: bool)
    decreases Repr()
    ensures this as Object == other ==> b
  {
    && other is DList
    && equalsDList(other as DList)
  }

  predicate equalsDList(other: DList) 
    ensures this as Object == other ==> equalsDList(other)
  {
    match (this, other)
    case (Nil, Nil) => true
    case (Cons(lhead, ltail), Cons(rhead, rtail)) =>
      lhead == rhead && ltail.equalsDList(rtail)
    case (_, _) => false
  }

  lemma equalsDListSymmetric(other: DList)
    requires equalsDList(other)
    ensures other.equalsDList(this)
  {
    match (this, other) {
      case (Nil, Nil) => {}
      case (Cons(lhead, ltail), Cons(rhead, rtail)) => {
        ltail.equalsDListSymmetric(rtail);
      }
      case (_, _) => {}
    }
  }

  lemma equalsSymmetric(other: Object)
    requires valid()
    requires other.valid()
    requires equals(other)
    ensures other.equals(this)
  {
    assert other is DList;
    equalsDListSymmetric(other as DList);
  }
}

datatype DNull extends Object = DNull {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  predicate equals(obj: Object)
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
  {
    obj is DNull
  }

  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    ensures obj.equals(this)
  {}
}

type byte = x
  | 0 <= x < 256

type float = real

type double = real


trait List<T> extends Object, object {

  ghost const elements: seq<T>

  predicate equals(obj: Object)
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
    ensures equals(obj) <==>
      && obj is List<T> 
      && var other := obj as List<T>;
      && elements == other.elements
}