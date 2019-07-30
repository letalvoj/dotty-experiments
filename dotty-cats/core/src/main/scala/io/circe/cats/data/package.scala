package io.circe.cats

import io.circe.cats.kernel.Monoid

package object data {

  type NonEmptyStream[A] = OneAnd[Stream, A]
  type ValidatedNel[+E, +A] = Validated[NonEmptyList[E], A]
  type IorNel[+B, +A] = Ior[NonEmptyList[B], A]
  type IorNec[+B, +A] = Ior[NonEmptyChain[B], A]
  type IorNes[B, +A] = Ior[NonEmptySet[B], A]
  type EitherNel[+E, +A] = Either[NonEmptyList[E], A]
  type EitherNec[+E, +A] = Either[NonEmptyChain[E], A]
  type EitherNes[E, +A] = Either[NonEmptySet[E], A]
  type ValidatedNec[+E, +A] = Validated[NonEmptyChain[E], A]

  def NonEmptyStream[A](head: A, tail: Stream[A] = Stream.empty): NonEmptyStream[A] =
    OneAnd(head, tail)

  def NonEmptyStream[A](head: A, tail: A*): NonEmptyStream[A] =
    OneAnd(head, tail.toStream)

  type NonEmptyMap[K, +A] = NonEmptyMapImpl.Type[K, A]
  val NonEmptyMap = NonEmptyMapImpl

  type NonEmptySet[A] = NonEmptySetImpl.Type[A]
  val NonEmptySet = NonEmptySetImpl

  type ReaderT[F[_], -A, B] = Kleisli[F, A, B]
  val ReaderT = Kleisli

  type Reader[-A, B] = ReaderT[Id, A, B]

  object Reader {
    def apply[A, B](f: A => B): Reader[A, B] = ReaderT[Id, A, B](f)

    def local[A, R](f: R => R)(fa: Reader[R, A]): Reader[R, A] = Kleisli.local(f)(fa)
  }

  type Writer[L, V] = WriterT[Id, L, V]
  object Writer {
    def apply[L, V](l: L, v: V): WriterT[Id, L, V] = WriterT[Id, L, V]((l, v))

    def value[L: Monoid, V](v: V): Writer[L, V] = WriterT.value(v)

    def tell[L](l: L): Writer[L, Unit] = WriterT.tell(l)

    def listen[L, V](writer: Writer[L, V]): Writer[L, (V, L)] =
      WriterT.listen(writer)
  }

  type IndexedState[S1, S2, A] = IndexedStateT[Eval, S1, S2, A]
  object IndexedState extends IndexedStateFunctions

  /**
   * `StateT[F, S, A]` is similar to `Kleisli[F, S, A]` in that it takes an `S`
   * argument and produces an `A` value wrapped in `F`. However, it also produces
   * an `S` value representing the updated state (which is wrapped in the `F`
   * context along with the `A` value.
   */
  type StateT[F[_], S, A] = IndexedStateT[F, S, S, A]
  object StateT extends StateTFunctions with CommonStateTConstructors0

  type State[S, A] = StateT[Eval, S, A]
  object State extends StateFunctions
  /*type Store[S, A] = RepresentableStore[S => *, S, A]
  object Store {
    import cats.instances.function._
    def apply[S, A](f: S => A, s: S): Store[S, A] =
      RepresentableStore[[a] =>> (S => a), S, A](f, s)
  }*/
}