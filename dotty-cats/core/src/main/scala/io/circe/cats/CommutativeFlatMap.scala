package io.circe.cats

/**
 * Commutative FlatMap.
 *
 * Further than a FlatMap, which just allows composition of dependent effectful functions,
 * in a Commutative FlatMap those functions can be composed in any order, which guarantees
 * that their effects do not interfere.
 *
 * Must obey the laws defined in cats.laws.CommutativeFlatMapLaws.
 */
trait CommutativeFlatMap[F[_]] extends FlatMap[F] with CommutativeApply[F]

object CommutativeFlatMap {
  def apply[F[_]] given (F: CommutativeFlatMap[F]): CommutativeFlatMap[F] = F
}