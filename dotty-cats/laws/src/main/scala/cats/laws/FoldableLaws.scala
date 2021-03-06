package io.circe.cats.laws

import io.circe.cats.{Eval, Foldable, Id, Later, Now}
import io.circe.cats.kernel.{Eq, Monoid}
import io.circe.cats.kernel.laws.IsEq
import given io.circe.cats.syntax.eq._
import given io.circe.cats.syntax.foldable._
import given io.circe.cats.syntax.semigroup._
import scala.collection.mutable.ListBuffer

trait FoldableLaws[F[_]] given (F: Foldable[F]) extends UnorderedFoldableLaws[F] {

  def foldRightLazy[A](fa: F[A]): Boolean = {
    var i = 0
    F.foldRight(fa, Eval.now("empty")) { (_, _) =>
        i += 1
        Eval.now("not empty")
      }
      .value
    i == (if (F.isEmpty(fa)) 0 else 1)
  }

  def leftFoldConsistentWithFoldMap[A, B](
    fa: F[A],
    f: A => B
  ) given (M: Monoid[B]): IsEq[B] =
    fa.foldMap(f) <-> fa.foldLeft(M.empty) { (b, a) =>
      b |+| f(a)
    }

  def rightFoldConsistentWithFoldMap[A, B](
    fa: F[A],
    f: A => B
  ) given (M: Monoid[B]): IsEq[B] =
    fa.foldMap(f) <-> fa.foldRight(Later(M.empty))((a, lb) => lb.map(f(a) |+| _)).value

  def existsConsistentWithFind[A](fa: F[A], p: A => Boolean): Boolean =
    F.exists(fa)(p) == F.find(fa)(p).isDefined

  /**
   * Monadic folding with identity monad is analogous to `foldLeft`.
   */
  def foldMIdentity[A, B](
    fa: F[A],
    b: B,
    f: (B, A) => B
  ): IsEq[B] =
    F.foldM[Id, A, B](fa, b)(f) <-> F.foldLeft(fa, b)(f)

  /**
   * `reduceLeftOption` consistent with `reduceLeftToOption`
   */
  def reduceLeftOptionConsistentWithReduceLeftToOption[A](
    fa: F[A],
    f: (A, A) => A
  ): IsEq[Option[A]] =
    F.reduceLeftOption(fa)(f) <-> F.reduceLeftToOption(fa)(identity)(f)

  /**
   * `reduceRightOption` consistent with `reduceRightToOption`
   */
  def reduceRightOptionConsistentWithReduceRightToOption[A](
    fa: F[A],
    f: (A, A) => A
  ): IsEq[Option[A]] = {
    val g: (A, Eval[A]) => Eval[A] = (a, ea) => ea.map(f(a, _))
    F.reduceRightOption(fa)(g).value <-> F.reduceRightToOption(fa)(identity)(g).value
  }

  def getRef[A](fa: F[A], idx: Long): IsEq[Option[A]] =
    F.get(fa)(idx) <-> (if (idx < 0L) None
                        else
                          F.foldM[[x] =>> Either[A, x], A, Long](fa, 0L) { (i, a) =>
                            if (i == idx) Left(a) else Right(i + 1L)
                          } match {
                            case Left(a)  => Some(a)
                            case Right(_) => None
                          })

  def foldRef[A](fa: F[A]) given (A: Monoid[A]): IsEq[A] =
    F.fold(fa) <-> F.foldLeft(fa, A.empty) { (acc, a) =>
      A.combine(acc, a)
    }

  def toListRef[A](fa: F[A]): IsEq[List[A]] =
    F.toList(fa) <-> F
      .foldLeft(fa, ListBuffer.empty[A]) { (buf, a) =>
        buf += a
      }
      .toList

  def filter_Ref[A](fa: F[A], p: A => Boolean): IsEq[List[A]] =
    F.filter_(fa)(p) <-> F
      .foldLeft(fa, ListBuffer.empty[A]) { (buf, a) =>
        if (p(a)) buf += a else buf
      }
      .toList

  def takeWhile_Ref[A](fa: F[A], p: A => Boolean): IsEq[List[A]] =
    F.takeWhile_(fa)(p) <-> F
      .foldRight(fa, Now(List.empty[A])) { (a, llst) =>
        if (p(a)) llst.map(a :: _) else Now(Nil)
      }
      .value

  def dropWhile_Ref[A](fa: F[A], p: A => Boolean): IsEq[List[A]] =
    F.dropWhile_(fa)(p) <-> F
      .foldLeft(fa, ListBuffer.empty[A]) { (buf, a) =>
        if (buf.nonEmpty || !p(a)) buf += a else buf
      }
      .toList

  def collectFirstSome_Ref[A, B](fa: F[A], f: A => Option[B]): IsEq[Option[B]] =
    F.collectFirstSome(fa)(f) <-> F.foldLeft(fa, Option.empty[B]) { (ob, a) =>
      if (ob.isDefined) ob else f(a)
    }

  def collectFirst_Ref[A, B](fa: F[A], pf: PartialFunction[A, B]): IsEq[Option[B]] =
    F.collectFirst(fa)(pf) <-> F.collectFirstSome(fa)(pf.lift)

  def orderedConsistency[A: Eq](x: F[A], y: F[A]) given Eq[F[A]]: IsEq[List[A]] =
    if (x === y) (F.toList(x) <-> F.toList(y))
    else List.empty[A] <-> List.empty[A]
}

object FoldableLaws {
  def apply[F[_]] given Foldable[F]: FoldableLaws[F] =
    new FoldableLaws[F] with UnorderedFoldableLaws[F] {}
}
