package io.circe

import cats.data.Validated.Invalid
import cats.data.{ Chain, NonEmptyList, Validated }
import cats.kernel.Eq
import given cats.syntax.either._
import given cats.syntax.eq._
import given cats.syntax.foldable._
import given cats.syntax.monoid._
import cats.laws.discipline.{ MonadErrorTests, SemigroupKTests }
import io.circe.CursorOp.{ DownArray, DownN }
import io.circe.jawn.parse
import given io.circe.syntax._
import io.circe.testing.CodecTests
import io.circe.tests.CirceSuite
import io.circe.tests.examples.WrappedOptionalField
import scala.util.{ Failure, Success, Try }
import scala.util.control.NoStackTrace

object DecoderSuite extends CirceSuite { //with LargeNumberDecoderTests {
  checkLaws("Decoder[Int]", MonadErrorTests[Decoder, DecodingFailure].monadError[Int, Int, Int])
  checkLaws("Decoder[Int]", SemigroupKTests[Decoder].semigroupK[Int])

  /*private[this] def transformations[T] = Table[Decoder[T] => Decoder[T]](
    "transformation",
    _.prepare(identity),
    _.map(identity),
    _.emap(Right(_)),
    _.emapTry(Success(_))
  )

  private[this] def containerDecoders[T: Decoder] = Table[Decoder[_]](
    "decoder",
    Decoder[Set[T]],
    Decoder[List[T]],
    Decoder[Vector[T]],
    Decoder[Chain[T]]
  )

  "transformations should do nothing when used with identity" in check1(transformations[Int]) { transformation =>
    val decoder = transformation(Decoder[Int])
    check1 { (i: Int) =>
      assert(decoder.decodeJson(i.asJson) === Right(i))
      assert(decoder.decodeAccumulating(i.asJson.hcursor) === Validated.valid(i))
    }
  }

  "transformations should fail when called on failed decoder" in check1(transformations[Int]) { transformation =>
    val decoder = transformation(Decoder.failedWithMessage("Some message"))
    val failure = DecodingFailure("Some message", Nil)
    check1 { (i: Int) =>
      assert(decoder.decodeJson(i.asJson) === Left(failure))
      assert(decoder.decodeAccumulating(i.asJson.hcursor) === Validated.invalidNel(failure))
    }
  }

  "transformations should not break derived decoders when called on Decoder[Option[T]]" in
    check1(transformations[Option[String]]) { transformation =>
      implicit val decodeOptionString: Decoder[Option[String]] =
        transformation(Decoder.decodeOption(Decoder.decodeString))

      object Test {
        implicit val eqTest: Eq[Test] = Eq.fromUniversalEquals
        implicit val decodeTest: Decoder[Test] = Decoder.forProduct1("a")(Test.apply)
      }

      case class Test(a: Option[String])
      assert(Decoder[Test].decodeJson(Json.obj()) === Right(Test(None)))
      assert(Decoder[Test].decodeAccumulating(Json.obj().hcursor) === Validated.valid(Test(None)))
    }
  */

  test("prepare should move appropriately with downField") {
    check3 { (i: Int, k: String, m: Map[String, Int]) =>
      Decoder[Int].prepare(_.downField(k)).decodeJson(m.updated(k, i).asJson) === Right(i)
    }
  }

  test("emap should appropriately transform the result with an operation that can't fail") {
    check1 { (i: Int) =>
      Decoder[Int].emap(v => Right(v + 1)).decodeJson(i.asJson) === Right(i + 1)
    }
  }

  test("emap should appropriately transform the result with an operation that may fail") {
    check1 { (i: Int) =>
      val decoder = Decoder[Int].emap(v => if (v % 2 == 0) Right(v) else Left("Odd"))
      val expected = if (i % 2 == 0) Right(i) else Left(DecodingFailure("Odd", Nil))

      decoder.decodeJson(i.asJson) === expected
    }
  }

  test("emapTry should appropriately transform the result with an operation that can't fail") {
    check1 { (i: Int) =>
      Decoder[Int].emapTry(v => Success(v + 1)).decodeJson(i.asJson) === Right(i + 1)
    }
  }

  test("emapTry should appropriately transform the result with an operation that may fail") {
    check1 { (i: Int) =>
      val exception = new Exception("Odd") with NoStackTrace
      val decoder = Decoder[Int].emapTry(v => if (v % 2 == 0) Success(v) else Failure(exception))

      decoder.decodeJson(i.asJson).isRight == (i % 2 == 0)
    }
  }

  test("failedWithMessage should replace the message") {
    check1 { (json: Json) =>
      Decoder.failedWithMessage[Int]("Bad").decodeJson(json) === Left(DecodingFailure("Bad", Nil))
    }
  }

  test("An optional object field decoder should fail appropriately") {
    val decoder: Decoder[Option[String]] = Decoder.instance(
      _.downField("").downField("").as[Option[String]]
    )

    check1 { (json: Json) =>
      val result = decoder.apply(json.hcursor)

      json.asObject match {
        // The top-level value isn't an object, so we should fail.
        case None => result.isLeft
        case Some(o1) =>
          o1("") match {
            // The top-level object doesn't contain a "" key, so we should succeed emptily.
            case None => result === Right(None)
            case Some(j2) =>
              j2.asObject match {
                // The second-level value isn't an object, so we should fail.
                case None => result.isLeft
                case Some(o2) =>
                  o2("") match {
                    // The second-level object doesn't contain a "" key, so we should succeed emptily.
                    case None => result === Right(None)
                    // The third-level value is null, so we succeed emptily.
                    case Some(j3) if j3.isNull => result === Right(None)
                    case Some(j3) =>
                      j3.asString match {
                        // The third-level value isn't a string, so we should fail.
                        case None => result.isLeft
                        // The third-level value is a string, so we should have decoded it.
                        case Some(s3) => result === Right(Some(s3))
                      }
                  }
              }
          }
      }
    }
  }

  test("An optional array position decoder should fail appropriately") {
    val decoder: Decoder[Option[String]] = Decoder.instance(
      _.downN(0).downN(1).as[Option[String]]
    )

    check1 { (json: Json) =>
      val result = decoder.apply(json.hcursor)

      json.asArray match {
        // The top-level value isn't an array, so we should fail.
        case None => result.isLeft
        case Some(a1) =>
          a1.lift(0) match {
            // The top-level array is empty, so we should succeed emptily.
            case None => result === Right(None)
            case Some(j2) =>
              j2.asArray match {
                // The second-level value isn't an array, so we should fail.
                case None => result.isLeft
                case Some(a2) =>
                  a2.lift(1) match {
                    // The second-level array doesn't have a second element, so we should succeed emptily.
                    case None => result === Right(None)
                    // The third-level value is null, so we succeed emptily.
                    case Some(j3) if j3.isNull => result === Right(None)
                    case Some(j3) =>
                      j3.asString match {
                        // The third-level value isn't a string, so we should fail.
                        case None => result.isLeft
                        // The third-level value is a string, so we should have decoded it.
                        case Some(s3) => result === Right(Some(s3))
                      }
                  }
              }
          }
      }
    }
  }

  test("An optional top-level decoder should fail appropriately") {
    val decoder: Decoder[Option[String]] = Decoder.instance(_.as[Option[String]])

    check1 { (json: Json) =>
      val result = decoder.apply(json.hcursor)

      if (json.isNull) {
        result === Right(None)
      } else
        json.asString match {
          case Some(str) => result === Right(Some(str))
          case None      => result.isLeft
        }
    }
  }

  test("A nested optional decoder should accumulate failures") {
    val pair = Json.arr(Json.fromInt(1), Json.fromInt(2))

    val result = Decoder[Option[(String, String)]].decodeAccumulating(pair.hcursor)
    val expected = Validated.invalid(
      NonEmptyList.of(DecodingFailure("String", List(DownN(0))), DecodingFailure("String", List(DownN(1))))
    )

    assert(result === expected)
  }

  test("instanceTry should provide instances that succeed or fail appropriately") {
    check1 { (json: Json) =>
      val exception = new Exception("Not an Int")
      val expected = json.hcursor.as[Int].leftMap(_ => DecodingFailure.fromThrowable(exception, Nil))
      val instance = Decoder.instanceTry(c => Try(c.as[Int].getOrElse(throw exception)))

      instance.decodeJson(json) === expected
    }
  }

  test("Decoder[Byte] should fail on out-of-range values (#83)") {
    check1 { (l: Long) =>
      val json = Json.fromLong(l)
      val result = Decoder[Byte].apply(json.hcursor)

      if (l.toByte.toLong == l) result === Right(l.toByte) else result.isLeft
    }
  }

  test("Decoder[Byte] should fail on non-whole values (#83)") {
    check1 { (d: Double) =>
      val json = Json.fromDoubleOrNull(d)
      val result = Decoder[Byte].apply(json.hcursor)

      d.isWhole || result.isLeft
    }
  }

  test("Decoder[Byte] should succeed on whole decimal values (#83)") {
    check2 { (v: Byte, n: Byte) =>
      val zeros = "0" * (math.abs(n.toInt) + 1)
      val Right(json) = parse(s"$v.$zeros")

      Decoder[Byte].apply(json.hcursor) === Right(v)
    }
  }

  test("Decoder[Short] should fail on out-of-range values (#83)") {
    check1 { (l: Long) =>
      val json = Json.fromLong(l)
      val result = Decoder[Short].apply(json.hcursor)

      if (l.toShort.toLong == l) result === Right(l.toShort) else result.isLeft
    }
  }

  test("Decoder[Short] should fail on non-whole values (#83)") {
    check1 { (d: Double) =>
      val json = Json.fromDoubleOrNull(d)
      val result = Decoder[Short].apply(json.hcursor)

      d.isWhole || result.isLeft
    }
  }

  test("Decoder[Short] should succeed on whole decimal values (#83)") {
    check2 { (v: Short, n: Byte) =>
      val zeros = "0" * (math.abs(n.toInt) + 1)
      val Right(json) = parse(s"$v.$zeros")

      Decoder[Short].apply(json.hcursor) === Right(v)
    }
  }

  test("Decoder[Int] should fail on out-of-range values (#83)") {
    check1 { (l: Long) =>
      val json = Json.fromLong(l)
      val result = Decoder[Int].apply(json.hcursor)

      if (l.toInt.toLong == l) result === Right(l.toInt) else result.isLeft
    }
  }

  test("Decoder[Int] should fail on non-whole values (#83)") {
    check1 { (d: Double) =>
      val json = Json.fromDoubleOrNull(d)
      val result = Decoder[Int].apply(json.hcursor)

      d.isWhole || result.isLeft
    }
  }

  test("Decoder[Int] should succeed on whole decimal values (#83)") {
    check2 { (v: Int, n: Byte) =>
      val zeros = "0" * (math.abs(n.toInt) + 1)
      val Right(json) = parse(s"$v.$zeros")

      Decoder[Int].apply(json.hcursor) === Right(v)
    }
  }

  test("Decoder[Long] should fail on out-of-range values (#83)") {
    check1 { (i: BigInt) =>
      val json = Json.fromBigDecimal(BigDecimal(i))
      val result = Decoder[Long].apply(json.hcursor)

      if (BigInt(i.toLong) == i) result === Right(i.toLong) else result.isLeft
    }
  }

  test("Decoder[Int] should fail on non-whole values (#83)") {
    check1 { (d: Double) =>
      val json = Json.fromDoubleOrNull(d)
      val result = Decoder[Long].apply(json.hcursor)

      d.isWhole || result.isLeft
    }
  }

  test("Decoder[Float] should attempt to parse string values as doubles (#173)") {
    check1 { (d: Float) =>
      val Right(json) = parse("\"" + d.toString + "\"")

      Decoder[Float].apply(json.hcursor) === Right(d)
    }
  }

  test("Decoder[Float] should match the rounding of Float.parseFloat (#1063)") {
    check1 { (d: Double) =>
      val Right(json) = parse(d.toString)

      Decoder[Float].apply(json.hcursor) === Right(java.lang.Float.parseFloat(d.toString))
    }
  }

  test("Decoder[Float] should match the rounding of Float.parseFloat for known problematic inputs (#1063)") {
    val bad1 = "1.199999988079071"
    val bad2 = "7.038531E-26"

    val Right(json1) = parse(bad1)
    val Right(json2) = parse(bad2)

    assert(Decoder[Float].apply(json1.hcursor) === Right(java.lang.Float.parseFloat(bad1)))
    assert(Decoder[Float].apply(json2.hcursor) === Right(java.lang.Float.parseFloat(bad2)))
  }

  test("Decoder[Double] should attempt to parse string values as doubles (#173)") {
    check1 { (d: Double) =>
      val Right(json) = parse("\"" + d.toString + "\"")

      Decoder[Double].apply(json.hcursor) === Right(d)
    }
  }

  test("Decoder[BigInt] should fail when producing a value would be intractable") {
    val Right(bigNumber) = parse("1e2147483647")

    assert(Decoder[BigInt].apply(bigNumber.hcursor).isLeft)
  }

  /*"Decoder[Enumeration] should parse Scala Enumerations") {
    object WeekDay extends Enumeration {
      type WeekDay = Value
      val Mon, Tue, Wed, Thu, Fri, Sat, Sun = Value
    }

    val decoder = Decoder.decodeEnumeration(WeekDay)
    val Right(friday) = parse("\"Fri\"")

    assert(decoder.apply(friday.hcursor) == Right(WeekDay.Fri))
  }

  "Decoder[Enumeration] should fail on unknown values in Scala Enumerations") {
    object WeekDay extends Enumeration {
      type WeekDay = Value
      val Mon, Tue, Wed, Thu, Fri, Sat, Sun = Value
    }

    val decoder = Decoder.decodeEnumeration(WeekDay)
    val Right(friday) = parse("\"Friday\"")

    assert(decoder.apply(friday.hcursor).isLeft)
  }*/

  val isPositive: Int => Boolean = _ > 0
  val isOdd: Int => Boolean = _ % 2 != 0

  test("ensure should fail appropriately on an invalid result") {
    check1 { (i: Int) =>
      val message = "Not positive!"

      val decodePositiveInt: Decoder[Int] = Decoder[Int].ensure(_ > 0, message)
      val expected = if (i > 0) Right(i) else Left(DecodingFailure(message, Nil))

      decodePositiveInt.decodeJson(Json.fromInt(i)) === expected
    }
  }

  test("ensure should only include the first failure when chained, even in error-accumulation mode") {
    check1 { (i: Int) =>
      val positiveMessage = "Not positive!"
      val oddMessage = "Not odd!"

      val badDecodePositiveOddInt: Decoder[Int] =
        Decoder[Int].ensure(isPositive, positiveMessage).ensure(isOdd, oddMessage)

      val expected = if (isPositive(i)) {
        if (isOdd(i)) {
          Validated.valid(i)
        } else {
          Validated.invalidNel(DecodingFailure(oddMessage, Nil))
        }
      } else {
        Validated.invalidNel(DecodingFailure(positiveMessage, Nil))
      }

      badDecodePositiveOddInt.decodeAccumulating(Json.fromInt(i).hcursor) === expected
    }
  }

  test("ensure should not include failures it hasn't checked for") {
    val decodePositiveInt: Decoder[Int] =
      Decoder[Int].ensure(isPositive, "Not positive!")

    val expected = Validated.invalidNel(DecodingFailure("Int", Nil))

    assert(decodePositiveInt.decodeAccumulating(Json.Null.hcursor) === expected)
  }

  test("ensure should include all given failures in error-accumulation mode") {
    check1 { (i: Int) =>
      val positiveMessage = "Not positive!"
      val oddMessage = "Not odd!"

      val decodePositiveOddInt: Decoder[Int] =
        Decoder[Int].ensure(
          i =>
            (if (isPositive(i)) Nil else List(positiveMessage)) ++
              (if (isOdd(i)) Nil else List(oddMessage))
        )

      val expected = if (isPositive(i)) {
        if (isOdd(i)) {
          Validated.valid(i)
        } else {
          Validated.invalidNel(DecodingFailure(oddMessage, Nil))
        }
      } else {
        if (isOdd(i)) {
          Validated.invalidNel(DecodingFailure(positiveMessage, Nil))
        } else {
          Validated.invalid(NonEmptyList.of(DecodingFailure(positiveMessage, Nil), DecodingFailure(oddMessage, Nil)))
        }
      }

      decodePositiveOddInt.decodeAccumulating(Json.fromInt(i).hcursor) === expected
    }
  }

  test("validate should fail appropriately on invalid input in fail-fast mode") {
    check1 { (i: Int) =>
      val message = "Not positive!"

      val decodePositiveInt: Decoder[Int] =
        Decoder[Int].validate(_.as[Int].exists(_ > 0), message)

      val expected = if (i > 0) Right(i) else Left(DecodingFailure(message, Nil))

      decodePositiveInt.decodeJson(Json.fromInt(i)) === expected
    }
  }

  test("validate should fail appropriately on invalid input in error-accumulation mode (#865)") {
    check1 { (i: Int) =>
      val message = "Not positive!"

      val decodePositiveInt: Decoder[Int] =
        Decoder[Int].validate(_.as[Int].exists(_ > 0), message)

      val expected = if (i > 0) Validated.valid(i) else Validated.invalidNel(DecodingFailure(message, Nil))

      decodePositiveInt.decodeAccumulating(Json.fromInt(i).hcursor) === expected
    }
  }

  test("validate should not infinitely recurse (#396)") {
    check1 { (i: Int) =>
      Decoder[Int].validate(_ => true, "whatever").apply(Json.fromInt(i).hcursor) === Right(i)
    }
  }

  test("validate should preserve error accumulation when validation succeeds") {
    val message = "This shouldn't work"

    trait Foo

    val decoder: Decoder[Foo] = new Decoder[Foo] {
      override def apply(c: HCursor): Decoder.Result[Foo] = Right(new Foo {})

      override def decodeAccumulating(c: HCursor): Decoder.AccumulatingResult[Foo] = Invalid(
        NonEmptyList.one(DecodingFailure(message, c.history))
      )
    }

    val validatingDecoder = decoder.validate(c => true, "Foobar")

    assert(validatingDecoder.decodeAccumulating(Json.True.hcursor).isInvalid)
  }

  test("validate should provide the generated error messages from HCursor when a function is passed") {
    case class Foo(x: Int, y: String)

    val decoder: Decoder[Foo] = Decoder.const(Foo(42, "meaning")).validate { c =>
      val maybeFieldsStr = for {
        json <- c.focus
        jsonKeys <- json.hcursor.keys
      } yield jsonKeys.mkString(",")
      maybeFieldsStr.getOrElse("") :: Nil
    }

    val Right(fooJson) = parse("""{"x":42, "y": "meaning"}""")

    assert(decoder.decodeJson(fooJson).swap.exists(_.message === "x,y"))
  }

  test("validate should not fail when the passed errors function returns an empty list") {
    val testValue = 42
    val decoder = Decoder[Int].validate(_ => Nil)

    val Right(intJson) = parse(testValue.toString)

    assert(decoder.decodeJson(intJson) === Right(testValue))
  }

  test("either should return the correct disjunct") {
    check1 { (value: Either[String, Boolean]) =>
      val json = value match {
        case Left(s)  => Json.fromString(s)
        case Right(b) => Json.fromBoolean(b)
      }

      Decoder[String].either(Decoder[Boolean]).decodeJson(json) === Right(value)
    }
  }

  private[this] val stateful = {
    import Decoder.state._
    Decoder.fromState(for {
      a <- decodeField[String]("a")
      b <- decodeField[String]("b")
      _ <- requireEmpty
    } yield a ++ b)
  }

  test("a stateful Decoder with requireEmpty should succeed when there are no leftover fields") {
    val json = Json.obj("a" -> "1".asJson, "b" -> "2".asJson)

    assert(stateful.decodeJson(json) === Right("12"))
  }

  test("a stateful Decoder with requireEmpty should fail when there are leftover fields") {
    val json = Json.obj("a" -> "1".asJson, "b" -> "2".asJson, "c" -> "3".asJson, "d" -> "4".asJson)

    assert(stateful.decodeJson(json).swap.exists(_.message === "Leftover keys: c, d"))
  }

  test("a stateful Decoder with requireEmpty should fail normally when a field is missing") {
    val json = Json.obj("a" -> "1".asJson)

    assert(stateful.decodeJson(json).swap.exists(_.message === "Attempt to decode value on failed cursor"))
  }

  private[this] val statefulOpt = {
    import Decoder.state._
    Decoder.fromState(for {
      a <- decodeField[Option[String]]("a")
      b <- decodeField[String]("b")
      _ <- requireEmpty
    } yield a.foldMap(identity) ++ b)
  }

  test("a stateful Decoder with requireEmpty and an optional value should succeed when there are no leftover fields and an optional field is missing") {
    val json = Json.obj("b" -> "2".asJson)

    assert(statefulOpt.decodeJson(json) === Right("2"))
  }

  test("a stateful Decoder with requireEmpty and an optional value should succeed when there are no leftover fields and an optional field is present") {
    val json = Json.obj("a" -> "1".asJson, "b" -> "2".asJson)

    assert(statefulOpt.decodeJson(json) === Right("12"))
  }

  test("a stateful Decoder with requireEmpty and an optional value should fail when there are leftover fields and an optional field is missing") {
    val json = Json.obj("b" -> "2".asJson, "c" -> "3".asJson, "d" -> "4".asJson)

    assert(statefulOpt.decodeJson(json).swap.exists(_.message === "Leftover keys: c, d"))
  }

  test("a stateful Decoder with requireEmpty and an optional value should fail when there are leftover fields and an optional field is present") {
    val json = Json.obj("a" -> "1".asJson, "b" -> "2".asJson, "c" -> "3".asJson, "d" -> "4".asJson)

    assert(statefulOpt.decodeJson(json).swap.exists(_.message === "Leftover keys: c, d"))
  }

  test("a stateful Decoder with requireEmpty and an optional value should fail normally when a field is missing and an optional field is present") {
    val json = Json.obj("a" -> "1".asJson)

    assert(statefulOpt.decodeJson(json).swap.exists(_.message === "Attempt to decode value on failed cursor"))
  }

  test("a stateful Decoder with requireEmpty and an optional value should fail normally when a field is missing and an optional field is missing") {
    val json = Json.obj()

    assert(statefulOpt.decodeJson(json).swap.exists(_.message === "Attempt to decode value on failed cursor"))
  }

  checkLaws("Codec[WrappedOptionalField]", CodecTests[WrappedOptionalField].codec)

  test("decodeSet should match sequence decoders") {
    check1 { (xs: List[Int]) =>
      Decoder.decodeSet[Int].decodeJson(xs.asJson) === Decoder[Seq[Int]].map(_.toSet).decodeJson(xs.asJson)
    }
  }

  test("decodeList should match sequence decoders") {
    check1 { (xs: List[Int]) =>
      Decoder.decodeList[Int].decodeJson(xs.asJson) === Decoder[Seq[Int]].map(_.toList).decodeJson(xs.asJson)
    }
  }

  test("decodeVector should match sequence decoders") {
    check1 { (xs: List[Int]) =>
      Decoder.decodeVector[Int].decodeJson(xs.asJson) === Decoder[Seq[Int]].map(_.toVector).decodeJson(xs.asJson)
    }
  }

  test("decodeChain should match sequence decoders") {
    check1 { (xs: List[Int]) =>
      Decoder.decodeChain[Int].decodeJson(xs.asJson) === Decoder[Seq[Int]].map(Chain.fromSeq(_)).decodeJson(xs.asJson)
    }
  }

  test("HCursor#history should be stack safe") {
    val size = 10000
    val json = List.fill(size)(1).asJson.mapArray(_ :+ true.asJson)
    val Left(DecodingFailure(_, history)) = Decoder[List[Int]].decodeJson(json)

    assert(history.size === size + 1)
  }

  case class NotDecodable(a: Int)
  implicit val decodeNotDecodable: Decoder[NotDecodable] = Decoder.failedWithMessage("Some message")

  /*test("container decoder should pass through error message from item") {
    check1(containerDecoders[NotDecodable]) {
      decoder =>
      val json = Json.arr(Json.obj("a" -> 1.asJson))
      decoder.decodeJson(json) === Left(DecodingFailure("Some message", List(DownArray)))
    }
  }*/
}
