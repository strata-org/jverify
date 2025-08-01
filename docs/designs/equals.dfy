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

  ghost predicate validComponent(component: Object)
    reads This(), Repr()
    decreases Repr(), 0
  {
      && component.This() <= Repr()
      // && component in Repr()
      && component.Repr() < Repr()
      // && this !in component.Repr()
      && component.valid()
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
    decreases Repr()
    ensures obj.equals(this)

  // Will also need a lemma for transitivity
}

//
// Supertype of all Java types mapped to Dafny reference types
//
trait ModifiableObject extends Object, object { 

  // Can't be declared on Object (unless it's a const instead)
  var repr: set<object>

  ghost predicate validModifiableComponent(component: ModifiableObject)
    reads This(), Repr()
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
    && validModifiableComponent(a)
    && validModifiableComponent(b)
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

      assert validModifiableComponent(a);
      assert other.validModifiableComponent(other.a);
      assert validModifiableComponent(b);
      assert other.validModifiableComponent(other.b);
      
      a.equals(other.a) && b.equals(other.b)
  }

  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    decreases Repr()
    ensures obj.equals(this)
  {
    var other: MyPair := obj as MyPair;
    a.equalsSymmetric(other.a);
    b.equalsSymmetric(other.b);
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
    decreases Repr()
    ensures obj.equals(this)
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
    decreases Repr()
    ensures obj.equals(this)
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
    decreases Repr()
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
    decreases Repr()
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
    decreases Repr()
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
    decreases Repr()
    ensures obj.equals(this)
  {}
}

type byte = x
  | 0 <= x < 256

type float = real

type double = real


trait ImmutableList<T extends Object> extends Object {

  const elements: seq<T>

  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1
  {
    forall i | 0 <= i < |elements| :: 
      var e := elements[i] as Object;
      validComponent(e)
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
    ensures equals(obj) <==>
      && obj is ImmutableList<T> 
      && var other := obj as ImmutableList<T>;
      && equalsImmutableList(other)

  predicate equalsImmutableList(other: ImmutableList<T>)
    requires valid()
    requires other.valid()
    reads This(), Repr(), other.This(), other.Repr()
    decreases Repr(), 0
  {
    && |elements| == |other.elements|
    && forall i | 0 <= i < |elements| :: 
      var e := elements[i] as Object;
      var e' := other.elements[i] as Object;
      assert validComponent(e);
      assert other.validComponent(e');
      (elements[i] as Object).equals(other.elements[i])
  }
}