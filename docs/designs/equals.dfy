//
// Supertype of all Java types
// Equivalent of java.lang.Object
//
trait Object {

  ghost predicate valid()
    reads This(), Repr()
    decreases Decreases(), 1

  // Generalized "this" in the Valid()/Repr idiom,
  // allowing for value types as well.
  ghost function This(): set<object> {
    if this is ModifiableObject then
      {this as ModifiableObject}
    else
      {}
  }

  ghost function Repr(): set<ModifiableObject> 
    reads This()

  ghost function Decreases(): set<Object>
    reads This()
  
  ghost predicate validComponent(component: Object)
    reads This(), Repr()
    decreases Decreases(), 0
  {
      && component.This() <= Repr()
      && component.Repr() < Repr()
      && component.valid()
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    decreases Decreases()
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

  function getClass(): Class
}

//
// Supertype of all Java types mapped to Dafny reference types
//
trait ModifiableObject extends Object, object { 

  // Can't be declared on Object (unless it's a const instead)
  ghost var repr: set<ModifiableObject>

  ghost function Repr(): set<ModifiableObject> 
    reads This()
  {
    if this is ModifiableObject then
     (this as ModifiableObject).repr
    else
     {}
  }

  ghost function Decreases(): set<Object>
    reads This()
  {
    set o <- Repr() :: o as Object
  }

  

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

// TODO: Needs to include class loaders too
datatype Class extends Object = Class(name: string) {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1
  {
    true
  }

  ghost function Repr(): set<ModifiableObject> 
    reads This()
  {
    {}
  }

  ghost function Decreases(): set<Object> 
    reads This()
  {
    {}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    decreases Repr()
    // Reflexivity
    ensures this as Object == obj ==> equals(obj)
  {
    obj is Class && (obj as Class) == this
  }

  // Symmetry
  // Can't be an intrinsic postcondition of equals
  // because we can't quantify over reference types.
  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    decreases Repr()
    ensures obj.equals(this)
  {}

  function getClass(): Class
  {
    Class("java.lang.Class")
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

class Constructable?MyPair extends MyPair {

  constructor init(a_: A, b_: B)
    requires a_.valid()
    requires b_.valid()
    ensures valid()
  {
    this.a := a_;
    this.b := b_;
    this.repr := {this, a_, b_} + a_.Repr() + b_.Repr();
    label before:
    new;
    // Working around Dafny issue (TODO: cut GHI)
    assert unchanged@before(a_.This());
    assert unchanged@before(a_.Repr());
    assert unchanged@before(b_.This());
    assert unchanged@before(b_.Repr());
  }

  function getClass(): Class {
    Class("MyPair")
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == Class("MyPair")
    ensures obj is Constructable?MyPair
}

type int32 = x: int
  | -2147483648 <= x && x <= 2147483647

trait A extends ModifiableObject {
  var f: int32

}

class Constructable?A extends A {

  constructor (f: int32) 
    ensures valid()
    ensures this.f == f
  {
    this.f := f;
    this.repr := {this};
  }

  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 0
  {
    repr == {this}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    ensures this as Object == obj ==> equals(obj)
    decreases Repr()
  {
    if !(obj.getClass() == Class("A")) then
      false
    else
      classIdentity(obj);
      var other: A := obj as A;
      f == other.f
  }

  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    decreases Repr()
    ensures obj.equals(this)
  {
    assert obj.getClass() == Class("A");
    classIdentity(obj);
  }

  function getClass(): Class {
    Class("A")
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == Class("A")
    ensures obj is Constructable?A
}



trait B extends A, object {
  var g: int32
}

class Constructable?B extends B {

  constructor (f: int32, g: int32) 
    ensures valid()
    ensures this.f == f
    ensures this.g == g
  {
    this.f := f;
    this.g := g;
    this.repr := {this};
  }

  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1
  {
    repr == {this}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    ensures this as Object == obj ==> equals(obj)
    decreases Repr()
  {
    if !(obj is B) then
      false
    else
      var other: B := obj as B;
      f == other.f && g == other.g
  }

  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    decreases Repr()
    ensures obj.equals(this)
  {
    assert obj is B;
  }

    function getClass(): Class {
    Class("A")
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == Class("B")
    ensures obj is Constructable?B
}

method WTF() {
  var a: Constructable?A := new Constructable?A(1);
  var b := new Constructable?B(1, 2);
  assert b is A;
  assert a.equals(b);
  if !((a as Object) is B) {
    assert !b.equals(a);
    a.equalsSymmetric(b);
    assert false;
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
    decreases Repr()
    ensures obj.equals(this)
  {
    assert obj as Object == this;
  }

  function getClass(): Class {
    Class("A")
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == Class("java.lang.Object")
    ensures obj is Constructable?ModifiableObject
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
  {
    true
  }

  ghost function Repr(): set<ModifiableObject> 
    reads This()
  {
    {}
  }

  ghost function Decreases(): set<Object> 
    reads This()
  {
    {}
  }

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

  function getClass(): Class {
    Class("java.lang.String")
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == Class("java.lang.String")
    ensures obj is DString
}

//
// Example value type
//
// Would be expressed in Java using record types
// or classes with the JVerify @Immutable annotation.
//
datatype DList<+T extends Object> extends Object = Cons(head: T, tail: DList) | Nil {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  ghost function Repr(): set<ModifiableObject> 
    reads This()
  {
    {}
  }

  ghost function Decreases(): set<Object> 
    reads This()
  {
    match this {
      case Cons(head, tail) => {this} + (head as Object).Decreases() + tail.Decreases()
      case Nil => {this}
    }
  }

  predicate equals(obj: Object): (b: bool)
    requires valid()
    requires obj.valid()
    reads This(), Repr(), obj.This(), obj.Repr()
    decreases Decreases()
    ensures this as Object == obj ==> b
  {
    && obj.getClass() == Class("DList")
    && (classIdentity(obj); equalsDList(obj as DList<Object>))
  }

  predicate equalsDList(other: DList<Object>) 
    requires valid()
    requires other.valid()
    reads This(), Repr(), other.This(), other.Repr()
    ensures this as Object == other ==> equalsDList(other)
    decreases Decreases(), 0
  {
    match (this, other)
    case (Nil, Nil) => true
    case (Cons(lhead, ltail), Cons(rhead, rtail)) =>
      // assert this decreases to ltail;
      // assert this decreases to lhead;
      (lhead as Object).equals(rhead as Object) && ltail.equalsDList(rtail)
    case (_, _) => false
  }

  lemma equalsDListSymmetric(other: DList<Object>)
    requires valid()
    requires other.valid()
    requires equalsDList(other)
    ensures other.equals(this)
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
    classIdentity(other);
    assert other is DList<Object>;
    equalsDListSymmetric(other as DList);
  }

  function getClass(): Class {
    Class("DList")
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == Class("DList")
    ensures obj is DList<Object>
}

datatype DNull extends Object = DNull(klass: Class) {
  ghost predicate valid()
    reads This(), Repr()
    decreases Repr(), 1

  ghost function Repr(): set<ModifiableObject> 
    reads This()
  {
    {}
  }

  ghost function Decreases(): set<Object> 
    reads This()
  {
    {}
  }

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

  function getClass(): Class {
    klass
  }

  // TODO: No classIdentity here, is that a problem?
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
      e.equals(e')
  }
}

trait SingletonList<T extends Object> extends ImmutableList<T> {

  // const value: T

  // ghost predicate valid()
  //   reads This(), Repr()
  //   decreases Repr(), 1
  // {
  //   forall i | 0 <= i < |elements| :: 
  //     var e := elements[i] as Object;
  //     validComponent(e)
  // }

  // predicate equals(obj: Object)
  //   requires valid()
  //   requires obj.valid()
  //   reads This(), Repr(), obj.This(), obj.Repr()
  //   decreases Repr()
  //   ensures this as Object == obj ==> equals(obj)
  //   ensures equals(obj) <==>
  //     && obj is ImmutableList<T> 
  //     && var other := obj as ImmutableList<T>;
  //     && equalsImmutableList(other)

  // predicate equalsImmutableList(other: ImmutableList<T>)
  //   requires valid()
  //   requires other.valid()
  //   reads This(), Repr(), other.This(), other.Repr()
  //   decreases Repr(), 0
  // {
  //   && |elements| == |other.elements|
  //   && forall i | 0 <= i < |elements| :: 
  //     var e := elements[i] as Object;
  //     var e' := other.elements[i] as Object;
  //     assert validComponent(e);
  //     assert other.validComponent(e');
  //     e.equals(e')
  // }
}