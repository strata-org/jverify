//
// Supertype of all Java types
// Equivalent of java.lang.Object
//
trait Object {

  static const objectKlass := Class.Make("java.lang.Object", [])

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

  // Generalized "Repr" in the Valid()/Repr idiom,
  // allowing for value types as well.
  // Only a function so that it can be delegated to
  // either an object field or a datatype deconstructor,
  // NOT so that it can be dynamically calculated
  // (because most of the time proving termination becomes difficult,
  // since Repr() is the common decreases clause
  // and we can't use that to prove termination of Repr() itself).
  ghost function Repr(): set<Object> 
    reads This()

  ghost function ReprObjects(): set<object> 
    reads This()
  {
    set o <- Repr() | o is ModifiableObject :: o as ModifiableObject
  }

  // Working around (TODO GHI)
  static ghost function AllReads(objects: set<Object>): set<object>
    reads set o <- objects | o is ModifiableObject :: o as ModifiableObject
  {
    set obj <- objects, o <- obj.ReprObjects() :: o
  }
  
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
  // because we can't quantify over possibly-reference types.
  lemma equalsSymmetric(obj: Object)
    requires valid()
    requires obj.valid()
    requires equals(obj)
    decreases Repr()
    ensures obj.equals(this)

  // (Will also need a lemma for transitivity)

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
    objectKlass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires objectKlass.isInstance(obj.getClass())
    ensures obj is Constructable?ModifiableObject
}

// TODO: Needs to include class loaders too
datatype Class extends Object = Class(name: string, superclasses: seq<Class>, allSupertypes: set<Class>) {

  static function klass(): Class
    ensures klass().valid()
  {
    Make("Class", [objectKlass])
  }

  static function Make(name: string, superclasses: seq<Class>): (result: Class)
    ensures result.valid()
  {
    Class(name, superclasses, (set s <- superclasses) + (set s <- superclasses, o: Class <- s.allSupertypes :: o))
  }

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
    obj is Class && (obj as Class).name == name
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
    klass()
  }

  predicate isAssignableFrom(cls: Class)
    ensures this == cls ==> isAssignableFrom(cls)
  {
    this == cls || this in cls.allSupertypes
  }

  predicate isInstance(obj: Object)
    ensures this == obj.getClass() ==> isInstance(obj)
  {
    isAssignableFrom(obj.getClass())
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

  static const klass := Class.Make("MyPair", [objectKlass])

  constructor init(a_: A, b_: B)
    requires a_.valid()
    requires b_.valid()
    ensures valid()
  {
    // Working around https://github.com/dafny-lang/dafny/issues/6324
    assert allocated(a_.ReprObjects());
    assert allocated(b_.ReprObjects());

    this.a := a_;
    this.b := b_;
    this.repr := {this, a_, b_} + a_.Repr() + b_.Repr();
    
    // label before:
    new;
    // Working around https://github.com/dafny-lang/dafny/issues/6324
    assert unchanged(a_.This());
    assert unchanged(a_.ReprObjects());
    assert unchanged(b_.This());
    assert unchanged(b_.ReprObjects());
  }

  function getClass(): Class {
    klass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires klass.isInstance(obj.getClass())
    ensures obj is Constructable?MyPair
}

type int32 = x: int
  | -2147483648 <= x && x <= 2147483647

trait A extends ModifiableObject {
  var f: int32
}

class Constructable?A extends A {

  static const klass := Class.Make("A", [objectKlass])

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
    if obj.getClass() != klass then
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
    assert obj.getClass() == klass;
    classIdentity(obj);
  }

  function getClass(): Class {
    klass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires klass.isInstance(obj)
    ensures obj is Constructable?A
}


trait B extends A, object {

  static const klass := Class.Make("B", [objectKlass, Constructable?A.klass])

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
    klass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == klass
    ensures obj is Constructable?B
}




type char16 = i: int
  | 0 <= i <= 65535

//
// Equivalent of java.lang.String
//
datatype DString extends Object = JS(elements: seq<char16>) {

  static const klass := Class.Make("DString", [objectKlass])

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
    klass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == klass
    ensures obj is DString
}

//
// Example value type
//
// Would be expressed in Java using record types
// or classes with the JVerify @Immutable annotation.
//
datatype DList<T extends Object> extends Object = Cons(head: T, tail: DList, ghost repr: set<Object>) | Nil {

  static const klass := Class.Make("DList", [objectKlass])

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
    && obj.getClass() == klass
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
    klass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == klass
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
    ensures valid() ==>
      forall i | 0 <= i < |elements| :: 
        var e := elements[i] as Object;
        validComponent(e)

  function size(): int
    requires valid()
    reads This(), ReprObjects()
    ensures size() == |elements|

  function get(index: int): T
    requires valid()
    requires 0 <= index < size()
    reads This(), ReprObjects()
    ensures get(index) == elements[index]

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
    ensures equals(obj) <==>
      && ImmutableList<Object>.isInstance?(obj)
      && var other := obj as ImmutableList<Object>;
      && equalsImmutableList(other)

  ghost predicate equalsImmutableList(other: ImmutableList<Object>)
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

  // TODO: Convenient to generate this for every class
  static predicate {:axiom} isInstance?(obj: Object)
    ensures isInstance?(obj) == Constructable?ImmutableList.klass.isInstance(obj)
    ensures isInstance?(obj) ==> obj is ImmutableList<Object>
}

class Constructable?ImmutableList {
  static const klass := Class.Make("ImmutableList", [Object.objectKlass])
}

datatype SingletonList<T extends Object> extends ImmutableList<T> = SingletonList(value: T, ghost repr: set<Object>) {

  static const klass := Class.Make("SingletonList", [objectKlass, Constructable?ImmutableList.klass])

  ghost predicate valid()
    reads This(), ReprObjects()
    decreases Repr(), 1
    ensures valid() ==>
      forall i | 0 <= i < |elements| :: 
        var e := elements[i] as Object;
        validComponent(e)
  {
    && elements == [value]
    && validComponent(value)
  }

  ghost function Repr(): set<Object> 
    reads This()
  {
    repr
  }
  
  function size(): int
    requires valid()
    reads This(), ReprObjects()
    ensures size() == |elements|
  {
    1
  }

  function get(index: int): T
    requires valid()
    requires 0 <= index < size()
    reads This(), ReprObjects()
    ensures get(index) == elements[index]
  {
    value
  }

  predicate equals(obj: Object)
    requires valid()
    requires obj.valid()
    reads This(), ReprObjects(), obj.This(), obj.ReprObjects()
    decreases Repr()
    ensures this as Object == obj ==> equals(obj)
    ensures equals(obj) <==>
      && ImmutableList<Object>.isInstance?(obj)
      && var other := obj as ImmutableList<Object>;
      && equalsImmutableList(other)
  {
    if !ImmutableList<Object>.isInstance?(obj) then
      false
    else
      var other := obj as ImmutableList<Object>;
      if other.size() != 1 then
        false
      else
        assert validComponent(value);
        (value as Object).equals(other.get(0))
  }

  lemma equalsSymmetric(other: Object)
    requires valid()
    requires other.valid()
    requires equals(other)
    decreases Repr()
    ensures other.equals(this)
  {
  }

  function getClass(): Class
  {
    klass
  }

  static lemma {:axiom} classIdentity(obj: Object)
    requires obj.getClass() == klass
    ensures obj is SingletonList<Object>
}

// TODO: function overloading in general
//      design @MethodContract?