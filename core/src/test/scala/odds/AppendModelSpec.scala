package ch.epfl.lamp.odds

import language.implicitConversions

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

import inference._

trait AppendModel extends OddsLang {

  def randomList(): Rand[List[Boolean]] =
    if (flip(0.5)) flip(0.5) :: randomList()
    else always(Nil)

  val t3 = List(true, true, true)
  val f2 = List(false, false)

  val appendModel1: Rand[List[Boolean]] = always(t3) ++ always(f2)
  val appendModel2: Rand[List[Boolean]] = flip(0.5) :: always(f2)
  val appendModel3: Rand[List[Boolean]] = always(t3) ++ randomList()

  val appendModel4: Rand[(List[Boolean], List[Boolean], List[Boolean])] = {
    // query: X ::: f2 == t3 ::: f2, solve for X
    val x = randomList()
    val xf2 = x ::: always(f2)
    (x, f2, xf2) when (xf2 === t3 ::: f2)
  }

  val appendModel5: Rand[(List[Boolean], List[Boolean])] = {
    // query: X ::: Y == t3 ::: f2, solve for X, Y
    val x = randomList()
    val y = randomList()
    val xy = x ::: y
    (x, y) when (xy === always(t3 ::: f2))
  }

  val appendModel6: Rand[(List[Boolean], List[Boolean])] = {
    // query: X ++ Y == t3 ++ f2, solve for X, Y
    val x = randomList()
    val y = randomList()
    val xy = x ++ y
    (x, y) when (xy === t3 ++ f2)
  }
}

trait AppendModelRandomTail extends OddsLang {

  // Now try lists where the tail is itself are a random variable.

  sealed abstract class RList[+A] {

    def ?::[B >: A](x: B): RList[B] = new ?::(x, always(this))

    def ++[B >: A](that: RList[B]): RList[B] = this match {
      case RNil     => that
      case x ?:: xs => new ?::(x, xs ++ that)
    }

    def asRandList: Rand[List[A]] = this match {
      case RNil     => always(Nil)
      case x ?:: xs => always(x) :: xs.asRandList
    }

    def ?==[B >: A](that: RList[B]): Rand[Boolean] = (this, that) match {
      case (x ?:: xs, y ?:: ys) if x == y => xs ?== ys
      case (RNil,     RNil)               => always(true)
      case _                              => always(false)
    }
  }
  final case object RNil extends RList[Nothing]
  final case class ?::[+A](hd: A, tl: Rand[RList[A]]) extends RList[A]

  // Implicit view to allow conversions from List to RList
  implicit final class AddAsRList[+A](xs: List[A]) {
    def asRList: RList[A] = xs match {
      case Nil     => RNil
      case x :: xs => x ?:: xs.asRList
    }
  }

  // Implicit conversions from RList to Rand[List]
  implicit def rlistAsRandList[A](xs: RList[A]): Rand[List[A]] = xs.asRandList
  implicit def randRlistAsRandList[A](xs: Rand[RList[A]]): Rand[List[A]] =
    xs.asRandList

  def randomRList(): Rand[RList[Boolean]] =
    if (flip(0.5)) flip(0.5) ?:: randomRList()
    else always(RNil)

  val t3 = List(true, true, true)
  val f2 = List(false, false)
  val t3c = t3.asRList
  val f2c = f2.asRList

  val appendModel1: Rand[List[Boolean]] = t3c ++ f2c
  val appendModel2: Rand[List[Boolean]] = flip(0.5) ?:: always(f2c)
  val appendModel3: Rand[List[Boolean]] = always(t3c) ++ randomRList()

  val appendModel4: Rand[(List[Boolean], List[Boolean], List[Boolean])] = {
    // query: X ++ f2 == t3 ++ f2, solve for X
    val x = randomRList()
    val xf2 = x ++ f2c
    (x.asRandList, f2, xf2.asRandList) when (xf2 ?== (t3c ++ f2c))
  }

  val appendModel5: Rand[(List[Boolean], List[Boolean])] = {
    // query: X ++ Y == t3 ++ f2, solve for X, Y
    val x = randomRList()
    val y = randomRList()
    val xy = x ++ y
    (x.asRandList, y.asRandList) when (xy ?== (t3c ++ f2c))
  }
}


class AppendModelSpec
    extends AppendModel
    with DepthBoundInference
    with OddsPrettyPrint
    with FlatSpec
    with ShouldMatchers {

  behavior of "AppendModel"

  it should "show the results of appendModel1" in {
    val (d, e) = reify(1000)(appendModel1)
    show(d, "appendModel1")
    d.toMap should equal (Map(List(true, true, true, false, false) -> 1.0))
    e should equal (0)
  }

  it should "show the results of appendModel2" in {
    val (d, e) = reify(1000)(appendModel2)
    show(d, "appendModel2")
    d.toMap should equal (Map(
      List(false, false, false) -> 0.5,
      List(true, false, false) -> 0.5))
    e should equal (0)
  }

  it should "show the results of appendModel3" in {
    val (d, e) = reify(5)(appendModel3)
    show(d, "appendModel3")
    d.size should be >= 5
    d should contain (t3, 0.5)
    d foreach {
      case (l, _) => l.take(3) should equal (t3)
    }
    e should be < 0.5
  }

  it should "show the results of appendModel4" in {
    val (d, e) = reify(1)(appendModel4)
    show(d, "appendModel4")
    d.size should be >= 1
    d foreach {
      case ((x, y, res), _) =>
        y should equal (f2)
        (x ::: y) should equal (res)
        res should equal (t3 ::: f2)
    }
    e should be < 0.5
  }

  it should "show the results of appendModel5" in {
    val (d, e) = reify(5)(appendModel5)
    show(d, "appendModel5")
    d.size should be >= 5
    d foreach {
      case ((x, y), _) =>
        (x ::: y) should equal (t3 ::: f2)
    }
    e should be < 0.5
  }

  it should "show the results of appendModel6" in {
    val (d, e) = reify(5)(appendModel6)
    show(d, "appendModel6")
    d.size should be >= 5
    d foreach {
      case ((x, y), _) =>
        (x ::: y) should equal (t3 ::: f2)
    }
    e should be < 0.5
  }
}

class AppendModelRandomTailSpec
    extends AppendModelRandomTail
    with DepthBoundInference
    with OddsPrettyPrint
    with FlatSpec
    with ShouldMatchers {

  it should "show the results of appendModel1" in {
    val (d, e) = reify(1000)(appendModel1)
    show(d, "appendModel1")
    d.toMap should equal (Map(List(true, true, true, false, false) -> 1.0))
    e should equal (0)
  }

  it should "show the results of appendModel2" in {
    val (d, e) = reify(1000)(appendModel2)
    show(d, "appendModel2")
    d.toMap should equal (Map(
      List(false, false, false) -> 0.5,
      List(true, false, false) -> 0.5))
    e should equal (0)
  }

  it should "show the results of appendModel3" in {
    val (d, e) = reify(5)(appendModel3)
    show(d, "appendModel3")
    d.size should be >= 5
    d should contain (t3, 0.5)
    d foreach {
      case (l, _) => l.take(3) should equal (t3)
    }
    e should be < 0.5
  }

  it should "show the results of appendModel4" in {
    val (d, e) = reify(1)(appendModel4)
    show(d, "appendModel4")
    d.size should be >= 1
    d foreach {
      case ((x, y, res), _) =>
        y should equal (f2)
        (x ++ y) should equal (res)
        res should equal (t3 ++ f2)
    }
    e should be < 0.5
  }

  it should "show the results of appendModel5" in {
    val (d, e) = reify(5)(appendModel5)
    show(d, "appendModel5")
    d.size should be >= 5
    d foreach {
      case ((x, y), _) =>
        (x ++ y) should equal (t3 ++ f2)
    }
    e should be < 0.5
  }
}