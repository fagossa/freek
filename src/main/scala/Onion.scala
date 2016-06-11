package freek

import cats.free.Free
import cats.{Applicative, Functor, FlatMap, Monad, Traverse}
import scala.annotation.implicitNotFound


/** Monadic Onion */
sealed trait Onion {
  type Build[t]
}

sealed trait :&:[H[_], T <: Onion] extends Onion {
  type Build[t] = H[T#Build[t]]
}

sealed trait Bulb extends Onion {
  type Build[t] = t
}

object Onion {

  def point[S <: Onion, A](a: A)(implicit pointer: Pointer[S]): S#Build[A] = pointer.point(a)

  def lift[S <: Onion, H[_], A](ha: H[A])(implicit lifter: Lifter[H, S]): S#Build[A] = lifter.lift(ha)

}


@implicitNotFound("could not prove onion ${S} is pointed; one of the element of the stack might lack an Applicative")
trait Pointer[S <: Onion] {
  def point[A](a: A): S#Build[A]
}

object Pointer {

  def apply[S <: Onion](implicit pointer: Pointer[S]) = pointer 

  implicit val bulb = new Pointer[Bulb] {
    def point[A](a: A): Bulb#Build[A] = a
  }

  implicit def cons[H[_]:Applicative, T <: Onion](
    implicit next: Pointer[T]
  ) = new Pointer[H :&: T] {
    def point[A](a: A): (H :&: T)#Build[A] = Applicative[H].pure(next.point(a))
  }

}

@implicitNotFound("could not lift ${H} into Onion ${S}; either ${S} does not contain a constructor of ${H}, or there is no Applicative Functor for a constructor of ${S}")
trait Lifter[H[_], S <: Onion] {
  def lift[A](ha: H[A]): S#Build[A]
}

object Lifter extends LifterLow {

  def apply[H[_], S <: Onion](implicit lifter: Lifter[H, S]) = lifter 

  // here Applicative isn't needed (just Functor) but you need it to add more elements to onion so...
  // implicit def first2[H[_]:Applicative, I[_]:Applicative, T <: Onion](
  //   implicit nextPointer: Pointer[T]
  // ): Lifter[λ[t => H[I[t]]], H :&: I :&: T] = new Lifter[λ[t => H[I[t]]], H :&: I :&: T] {
  //   def lift[A](hia: H[I[A]]): (H :&: I :&: T)#Build[A] = Functor[H].map(hia)(ia => Functor[I].map(ia)(a => nextPointer.point(a)))
  // }

  implicit def first2[H[_]:Applicative, I[_], T <: Onion](
    implicit next: Lifter[I, T]
  ): Lifter[λ[t => H[I[t]]], H :&: T] = new Lifter[λ[t => H[I[t]]], H :&: T] {
    def lift[A](hia: H[I[A]]): (H :&: T)#Build[A] = Functor[H].map(hia)(ia => next.lift(ia))
  }

  implicit def cons2[H[_]:Applicative, I[_], K[_]:Applicative, T <: Onion](
    implicit next: Lifter[λ[t => H[I[t]]], T]
  ): Lifter[λ[t => H[I[t]]], K :&: T] = new Lifter[λ[t => H[I[t]]], K :&: T] {
    def lift[A](hia: H[I[A]]): (K :&: T)#Build[A] = Applicative[K].pure(next.lift(hia))
  }
}

trait LifterLow {

  // here Applicative isn't needed (just Functor) but you need it to add more elements to onion so...
  implicit def first[H[_]:Applicative, T <: Onion](
    implicit nextPointer: Pointer[T]
  ): Lifter[H, H :&: T] = new Lifter[H, H :&: T] {
    def lift[A](ha: H[A]): (H :&: T)#Build[A] = Functor[H].map(ha)(a => nextPointer.point(a))
  }

  implicit def cons[H[_]:Functor:Applicative, K[_]:Applicative, T <: Onion](
    implicit next: Lifter[H, T]
  ): Lifter[H, K :&: T] = new Lifter[H, K :&: T] {
    def lift[A](ha: H[A]): (K :&: T)#Build[A] = Applicative[K].pure(next.lift(ha))
  }

}

@implicitNotFound("could not lift2 ${HA} into Onion ${S}; either ${S} does not contain a constructor of ${HA}, or there is no Applicative Functor for a constructor of ${S}")
trait Lifter2[HA, S <: Onion] {
  type A
  def lift2(ha: HA): S#Build[A]
}

object Lifter2 extends Lifter2Low {

  def apply[HA, S <: Onion](implicit lifter: Lifter2[HA, S]) = lifter 

  implicit def cons2[H[_]:Applicative, I[_], A0, T <: Onion](
    implicit next: Lifter2[I[A0], T]
  ): Lifter2[H[I[A0]], H :&: T] = new Lifter2[H[I[A0]], H :&: T] {
    type A = next.A
    def lift2(hia: H[I[A0]]): (H :&: T)#Build[A] = Functor[H].map(hia)(ia => next.lift2(ia))
  }
}

trait Lifter2Low {
  implicit def first[H[_]:Applicative, A0, T <: Onion](
    implicit nextPointer: Pointer[T]
  ): Lifter2[H[A0], H :&: T] = new Lifter2[H[A0], H :&: T] {
    type A = A0
    def lift2(ha: H[A0]): (H :&: T)#Build[A] = Functor[H].map(ha)(a => nextPointer.point(a))
  }

  implicit def cons[H[_]:Functor:Applicative, A0, K[_]:Applicative, T <: Onion](
    implicit next: Lifter2[H[A0], T]
  ): Lifter2[H[A0], K :&: T] = new Lifter2[H[A0], K :&: T] {
    type A = next.A
    def lift2(ha: H[A0]): (K :&: T)#Build[A] = Applicative[K].pure(next.lift2(ha))
  }


}

@implicitNotFound("could not compute a method for mapping over onion ${S}; either a member of the stack lacks a Functor, or its Functor instance is ambiguous")
trait Mapper[S <: Onion] {
  def map[A, B](fa: S#Build[A])(f: A => B): S#Build[B]
}

object Mapper {

  def apply[S <: Onion](implicit mapper: Mapper[S]) = mapper 

  implicit val bulb = new Mapper[Bulb] {
    def map[A, B](fa: Bulb#Build[A])(f: A => B): Bulb#Build[B] = f(fa)
  }

  implicit def cons[H[_]:Functor, T <: Onion](
    implicit next: Mapper[T]
  ) = new Mapper[H :&: T] {
    def map[A, B](fa: (H :&: T)#Build[A])(f: A => B): (H :&: T)#Build[B] = Functor[H].map(fa)(ta => next.map(ta)(f))
  }
}

@implicitNotFound("could not prove onion ${S} is a valid traversable stack; perhaps an element of the stack is lacking a Traverse")
trait Traverser[S <: Onion] {
  def traverse[G[_]: Applicative, A, B](sa: S#Build[A])(f: A => G[B]): G[S#Build[B]]
}

object Traverser {

  def apply[S <: Onion](implicit traverser: Traverser[S]) = traverser 

  implicit val bulb = new Traverser[Bulb] {
    def traverse[G[_]: Applicative, A, B](sa: Bulb#Build[A])(f: A => G[B]): G[Bulb#Build[B]] =
      f(sa)
  }

  implicit def cons[H[_]:Traverse, T <: Onion](
    implicit next: Traverser[T]
  ) = new Traverser[H :&: T] {
    def traverse[G[_]: Applicative, A, B](hta: (H :&: T)#Build[A])(f: A => G[B]): G[(H :&: T)#Build[B]] =
      Traverse[H].traverse(hta){ ta =>
        next.traverse[G, A, B](ta)(f)
      }
  }
}

@implicitNotFound("could not prove onion ${S} is a valid monadic stack; perhaps an element is lacking a Monad, or one of the sub-onion is lacking a Traverser")
trait Binder[S <: Onion] {
  def bind[A, B](fa: S#Build[A])(f: A => S#Build[B]): S#Build[B]
}

object Binder {

  def apply[S <: Onion](implicit binder: Binder[S]) = binder 

  implicit val nil = new Binder[Bulb] {
    def bind[A, B](fa: Bulb#Build[A])(f: A => Bulb#Build[B]): Bulb#Build[B] = f(fa)
  }

  // H must be a Functor, a FlatMap & an Applicative (for traverse)
  implicit def cons[H[_]:Monad, T <: Onion](
    implicit nextTraverser: Traverser[T], nextBinder: Binder[T]
  ) = new Binder[H :&: T] {
    def bind[A, B](fa: (H :&: T)#Build[A])(f: A => (H :&: T)#Build[B]): (H :&: T)#Build[B] =
      FlatMap[H].flatMap(fa){ ta =>
        val htb: H[T#Build[T#Build[B]]] = nextTraverser.traverse(ta){ a => f(a) }
        FlatMap[H].map(htb){ ttb => nextBinder.bind(ttb){ tb => tb } }
      }
  }
}


@implicitNotFound("could not expand Onion ${S1} into Onion ${S2}; either ${S1} must be a sub-onion of ${S2}")
trait Expander[S1 <: Onion, S2 <: Onion] {
  def expand[A](s1: S1#Build[A]): S2#Build[A]
}

object Expander {

  def apply[S1 <: Onion, S2 <: Onion](implicit expander: Expander[S1, S2]) = expander

  implicit val bulb = new Expander[Bulb, Bulb] {
    def expand[A](s1a: Bulb#Build[A]): Bulb#Build[A] = s1a
  } 

  // H must be a Functor, a FlatMap & an Applicative (for traverse)
  implicit def first[H[_]:Functor, S1 <: Onion, S2 <: Onion](
    implicit next: Expander[S1, S2]
  ) = new Expander[H :&: S1, H :&: S2] {
    def expand[A](sS1a: (H :&: S1)#Build[A]): (H :&: S2)#Build[A] =
      Functor[H].map(sS1a){ S1a => next.expand(S1a) }
  }

  // H must be a Functor, a FlatMap & an Applicative (for traverse)
  implicit def cons[H[_]:Applicative, S1 <: Onion, S2 <: Onion](
    implicit next: Expander[S1, S2]
  ) = new Expander[S1, H :&: S2] {
    def expand[A](hs1a: S1#Build[A]): (H :&: S2)#Build[A] =      
      Applicative[H].pure(next.expand(hs1a))
  }
}



trait DownRight[S <: Onion] {
  type OutS <: Onion
  type Out[_]

  def downRight[A](s: S#Build[A]): OutS#Build[Out[A]]
}

object DownRight {

  type Aux[S <: Onion, OutS0 <: Onion, Out0[_]] = DownRight[S] { type OutS = OutS0; type Out[t] = Out0[t] }

  def apply[S <: Onion](implicit dr: DownRight[S]) = dr

  implicit def first[H[_]]: DownRight.Aux[H :&: Bulb, Bulb, H] = new DownRight[H :&: Bulb] {
    type OutS = Bulb
    type Out[t] = H[t]

    def downRight[A](hka: (H :&: Bulb)#Build[A]): Bulb#Build[H[A]] = {
      hka
    }
  }

  implicit def cons[H[_]:Functor, S <: Onion, NextS <: Onion, Next[_]](
    implicit next: DownRight.Aux[S, NextS, Next]
  ): DownRight.Aux[H :&: S, H :&: NextS, Next] = new DownRight[H :&: S] {
    type OutS = H :&: NextS
    type Out[t] = Next[t]

    def downRight[A](hsa: (H :&: S)#Build[A]): (H :&: NextS)#Build[Next[A]] =
      Functor[H].map(hsa){ sa => next.downRight(sa) }
  }
}


trait UpLeft[H[_], S <: Onion] {
  type Out <: Onion

  def upLeft[A](s: S#Build[A]): Out#Build[A]
}


object UpLeft {

  def apply[H[_], S <: Onion](implicit upleft: UpLeft[H, S]) = upleft

  implicit def build[H[_]: Applicative, S <: Onion] = new UpLeft[H, S] {
    type Out = H :&: S
    def upLeft[A](s: S#Build[A]): (H :&: S)#Build[A] = Applicative[H].pure(s)
  }
}
