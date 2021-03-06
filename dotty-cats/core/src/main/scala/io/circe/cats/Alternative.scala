package io.circe.cats

trait Alternative[F[_]] extends Applicative[F] with MonoidK[F] { self =>

  /**
   * Fold over the inner structure to combine all of the values with
   * our combine method inherited from MonoidK. The result is for us
   * to accumulate all of the "interesting" values of the inner G, so
   * if G is Option, we collect all the Some values, if G is Either,
   * we collect all the Right values, etc.
   *
   * Example:
   * {{{
   * scala> import cats.implicits._
   * scala> val x: List[Vector[Int]] = List(Vector(1, 2), Vector(3, 4))
   * scala> Alternative[List].unite(x)
   * res0: List[Int] = List(1, 2, 3, 4)
   * }}}
   */
  def unite[G[_], A](fga: F[G[A]]) given (F: Monad[F], G: Foldable[G]): F[A] =
    F.flatMap(fga) { ga =>
      G.foldLeft(ga, empty[A])((acc, a) => combineK(acc, pure(a)))
    }

  /**
   * Separate the inner foldable values into the "lefts" and "rights"
   *
   * Example:
   * {{{
   * scala> import cats.implicits._
   * scala> val l: List[Either[String, Int]] = List(Right(1), Left("error"))
   * scala> Alternative[List].separate(l)
   * res0: (List[String], List[Int]) = (List(error),List(1))
   * }}}
   */
  def separate[G[_, _], A, B](fgab: F[G[A, B]]) given (F: Monad[F], G: Bifoldable[G]): (F[A], F[B]) = {
    val as = F.flatMap(fgab)(gab => G.bifoldMap(gab)(pure, _ => empty[A]) given algebra[A])
    val bs = F.flatMap(fgab)(gab => G.bifoldMap(gab)(_ => empty[B], pure) given algebra[B])
    (as, bs)
  }

  /**
   * Return ().pure[F] if `condition` is true, `empty` otherwise
   *
   * Example:
   * {{{
   * scala> import cats.implicits._
   * scala> def even(i: Int): Option[String] = Alternative[Option].guard(i % 2 == 0).as("even")
   * scala> even(2)
   * res0: Option[String] = Some(even)
   * scala> even(3)
   * res1: Option[String] = None
   * }}}
   */
  def guard(condition: Boolean): F[Unit] =
    if (condition) unit else empty

  override def compose[G[_]] given Applicative[G]: Alternative[[x] =>> F[G[x]]] =
    new ComposedAlternative[F, G] {
      val F = self
      val G = the[Applicative[G]]
    }
}

object Alternative {
  def apply[F[_]] given (F: Alternative[F]): Alternative[F] = F
}
