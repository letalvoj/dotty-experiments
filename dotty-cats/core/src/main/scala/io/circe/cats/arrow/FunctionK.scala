package io.circe.cats.arrow

import io.circe.cats.data.EitherK

/**
 * `FunctionK[F[_], G[_]]` is a functor transformation from `F` to `G`
 * in the same manner that function `A => B` is a morphism from values
 * of type `A` to `B`.
 * An easy way to create a FunctionK instance is to use the Polymorphic
 * lambdas provided by non/kind-projector v0.9+. E.g.
 * {{{
 *   val listToOption = λ[FunctionK[List, Option]](_.headOption)
 * }}}
 */
trait FunctionK[F[_], G[_]] extends PolyFunction with Serializable { self =>

  /**
   * Applies this functor transformation from `F` to `G`
   */
  def apply[A](fa: F[A]): G[A]

  /**
   * Composes two instances of FunctionK into a new FunctionK with this
   * transformation applied last.
   */
  def compose[E[_]](f: FunctionK[E, F]): FunctionK[E, G] =
    new FunctionK[E, G] {
      def apply[X](x: E[X]) = self(f(x))
    }

  /**
   * Composes two instances of FunctionK into a new FunctionK with this
   * transformation applied first.
   */
  def andThen[H[_]](f: FunctionK[G, H]): FunctionK[F, H] =
    f.compose(self)

  /**
   * Composes two instances of FunctionK into a new FunctionK that transforms
   * a [[cats.data.EitherK]] to a single functor.
   *
   * This transformation will be used to transform left `F` values while
   * `h` will be used to transform right `H` values.
   */
  //def or[H[_]](h: FunctionK[H, G]): FunctionK[EitherK[F, H, *], G] =
  //  λ[FunctionK[EitherK[F, H, *], G]](fa => fa.fold(self, h))

  /**
   * Composes two instances of `FunctionK` into a new `FunctionK` that transforms
   * one single functor to a [[cats.data.Tuple2K]] of two functors.
   *
   * {{{
   * scala> import cats.arrow.FunctionK
   * scala> val list2option = λ[FunctionK[List, Option]](_.headOption)
   * scala> val list2vector = λ[FunctionK[List, Vector]](_.toVector)
   * scala> val optionAndVector = list2option and list2vector
   * scala> optionAndVector(List(1,2,3))
   * res0: cats.data.Tuple2K[Option,Vector,Int] = Tuple2K(Some(1),Vector(1, 2, 3))
   * }}}
   */
  //def and[H[_]](h: FunctionK[F, H]): FunctionK[F, Tuple2K[G, H, *]] =
  //  λ[FunctionK[F, Tuple2K[G, H, *]]](fa => Tuple2K(self(fa), h(fa)))
}

object FunctionK {

  /**
   * The identity transformation of `F` to `F`
   */
  def id[F[_]]: FunctionK[F, F] = new FunctionK[F, F] { def apply[X](x: F[X]) = x }
}
