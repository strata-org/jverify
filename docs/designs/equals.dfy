//
// Supertype of all Java types
// Equivalent of java.lang.Object
//
trait Object {

  ghost predicate valid()
    reads This(), ReprObjects()
    decreases Repr(), 1

  // Generalized "this" in the Valid()/Repr idiom,
  // allowing for value types as well.
  ghost function This(): set<object> {
    if this is ModifiableObject then
      {this as ModifiableObject}
    else
      {}
  }

  ghost function Repr(): set<Object> 
    reads This()

  ghost function ReprObjects(): set<object> 
    reads This()
  {
    set o <- Repr() | o is ModifiableObject :: o as ModifiableObject
  }

  // lemma ReprObjectsAllocated()
  //   ensures forall o <- This() :: allocated(o)
  // {}
  
  ghost predicate validComponent(component: Object)
    reads This(), ReprObjects()
    decreases Repr(), 0
  {
      && component in Repr()
      && component.Repr() < Repr()
      && component.valid()
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
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

  function getClass(): Class
}

//
// Supertype of all Java types mapped to Dafny reference types
//
trait ModifiableObject extends Object, object { 

  // Can't be declared on Object (unless it's a const instead)
  ghost var repr: set<Object>

  ghost function Repr(): set<Object> 
    reads This()
  {
    if this is ModifiableObject then
     (this as ModifiableObject).repr
    else
     {}
  }

  ghost predicate validModifiableComponent(component: ModifiableObject)
    reads This(), ReprObjects()
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
    reads This(), ReprObjects()
    decreases Repr(), 1
  {
    true
  }

  ghost function Repr(): set<Object> 
    reads This()
  {
    {}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
    decreases Repr()
    // Reflexivity
    ensures this as Object == obj ==> equals(obj)
  {
    obj is Class && (obj as Class) == this
  }

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
    reads This(), ReprObjects()
    decreases Repr(), 1
  {
    && this in Repr()
    && validModifiableComponent(a)
    && validModifiableComponent(b)
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
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
    assert forall o <- a_.ReprObjects() :: allocated(o);
    this.a := a_;
    this.b := b_;
    this.repr := {this, a_, b_} + a_.Repr() + b_.Repr();
    label before:
    assert forall o <- a_.ReprObjects() :: allocated(o);
    new;
    // Working around Dafny issue (TODO: cut GHI)
    assert unchanged@before(a_.This());
    assert unchanged@before(a_.ReprObjects());
    assert unchanged@before(b_.This());
    assert unchanged@before(b_.ReprObjects());
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
    reads This(), ReprObjects()
    decreases Repr(), 0
  {
    repr == {this}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
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
    reads This(), ReprObjects()
    decreases Repr(), 1
  {
    repr == {this}
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
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
    reads This(), ReprObjects()
    decreases Repr(), 1

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
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
    reads This(), ReprObjects()
    decreases Repr(), 1
  {
    true
  }

  ghost function Repr(): set<Object> 
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
datatype DList<T extends Object> extends Object = Cons(head: T, tail: DList, ghost repr: set<Object>) | Nil {
  ghost predicate valid()
    reads This(), ReprObjects()
    decreases Repr(), 1
  {
    match (this)
    case Cons(head, tail, _) => validComponent(head as Object) && validComponent(tail as Object)
    case Nil => true
  }

  ghost function Repr(): set<Object> 
    reads This()
  {
    match (this)
    case Cons(_, _, repr) => repr
    case Nil => {}
  }

  predicate equals(obj: Object): (b: bool)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
    decreases Repr()
    ensures this as Object == obj ==> b
  {
    && obj.getClass() == Class("DList")
    && (classIdentity(obj); equalsDList(obj as DList<Object>))
  }

  predicate equalsDList(other: DList<Object>) 
    requires valid()
    requires other.valid()
    reads This(), ReprObjects(), other.This(), other.ReprObjects()
    ensures this as Object == other ==> equalsDList(other)
    decreases Repr(), 0
  {
    match (this, other)
    case (Nil, Nil) => true
    case (Cons(lhead, ltail, _), Cons(rhead, rtail, _)) =>
      assert validComponent(lhead);
      assert validComponent(ltail);
      assert other.validComponent(rhead);
      assert other.validComponent(rtail);
      (lhead as Object).equals(rhead as Object) && ltail.equalsDList(rtail)
    case (_, _) => false
  }

  lemma equalsDListSymmetric(other: DList<Object>)
    requires valid()
    requires other.valid()
    requires equalsDList(other)
    ensures other.equals(this)
    decreases Repr(), 0
  {
    match (this, other) {
      case (Nil, Nil) => {}
      case (Cons(lhead, ltail, _), Cons(rhead, rtail, _)) => {
        assert rtail.valid();
        assert validComponent(lhead);
        (lhead as Object).equalsSymmetric(rhead);
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

method DListTest() {
  var a1 := new Constructable?A(42);
  assert a1.valid();
  var a2 := new Constructable?A(43);
  assert a2.valid();
  
  var n: DList<A> := Nil;
  assert n.valid();
  var repr: set<Object> := {a1, n} + a1.Repr() + n.Repr();
  var l := Cons(a1, n, repr);
  assert n in l.Repr();
  assert l.valid();
}

trait ImmutableList<T extends Object> extends Object {

  ghost const elements: seq<T>

  ghost predicate valid()
    reads This(), ReprObjects()
    decreases Repr(), 1
  {
    forall i | 0 <= i < |elements| :: 
      var e := elements[i] as Object;
      validComponent(e)
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
    ensures equals(obj) <==>
      && obj is ImmutableList<T> 
      && var other := obj as ImmutableList<T>;
      && equalsImmutableList(other)

  ghost predicate equalsImmutableList(other: ImmutableList<T>)
    requires valid()
    requires other.valid()
    reads This(), ReprObjects(), other.This(), other.ReprObjects()
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

// TODO: function overloading in general
//      design @MethodContract?