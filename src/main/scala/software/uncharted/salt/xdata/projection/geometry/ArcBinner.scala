/**
 * Copyright © 2013-2017 Uncharted Software Inc.
 *
 * Property of Uncharted™, formerly Oculus Info Inc.
 *
 * http://uncharted.software/
 *
 * Released under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package software.uncharted.salt.xdata.projection.geometry

import scala.language.implicitConversions

/**
  * Helper functions to generate bin counts for arcs that traverse a tile.
  */
object ArcBinner {
  private val clockwiseQuarterTurn = DoubleRotation(0, 1, -1, 0)
  private val counterclockwiseQuarterTurn = DoubleRotation(0, -1, 1, 0)

  /**
    * Get the center point of an arc
    *
    * @param start The starting point of the arc
    * @param end The ending point of the arc
    * @param arcLength The length of the arc, in radians
    * @param clockwise true if the arc goes clockwise from the start to the end; false if it goes counter-clockwise.
    * @return The center of the arc as an (x,y) pair.
    */
  def getArcCenter (start: DoubleTuple, end: DoubleTuple, arcLength: Double, clockwise: Boolean): DoubleTuple = {
    val delta = end - start

    // Go from the midpoint of our chord to the midpoint of the circle
    // The amount by which to scale the chord to get to the center
    val chordElevationScale = math.tan(arcLength / 2.0)
    val rotation = if (clockwise) clockwiseQuarterTurn else counterclockwiseQuarterTurn
    (start + end) / 2.0 + rotation * delta / (2.0 * chordElevationScale)
  }

  /**
    * Get the radius of an arc
    *
    * @param start The starting point of the arc
    * @param end The ending point of the arc
    * @param arcLength The length of the arc, in radians
    * @return radius The radius of the described ars.
    */
  def getArcRadius (start: DoubleTuple, end: DoubleTuple, arcLength: Double): Double = {
    val delta = end - start
    val chordLength = delta.length

    (chordLength / 2.0) / math.sin(arcLength / 2.0)
  }

  /**
    * Find the angular length of an arc with a given chord length (for a circle of a given radius)
    *
    * @param radius The radius of the circle
    * @param chordLength The chord length on the circle
    * @return The angular length of the chord in radians
    */
  def getArcLength (radius: Double, chordLength: Double): Double = {
    // sin(1/2 theta) = 1/s chord length / radius
    2.0 * math.asin(chordLength / (2.0 * radius))
  }

  /**
    * Get the octant of the cartesian plane in which a given point lies. Octant 0 goes from the positive
    * x axis to the 45deg line x=y, octant 1 goes from that line to the positive y axis, etc.
    * Octants are closed on the counter-clockwise side, and open on the clockwise side.
    *
    * @param coords The x,y location of the query point expressed as a DoubleTuple
    * @return The octant number of the query point.
    */
  def getOctant (coords: DoubleTuple): Int = {
    getOctant(coords.x, coords.y)
  }

  /**
    * Get the octant of the cartesian plane in which a given point lies. Octant 0 goes from the positive
    * x axis to the 45deg line x=y, octant 1 goes from that line to the positive y axis, etc.
    * Octants are closed on the counter-clockwise side, and open on the clockwise side.
    *
    * @param x The x location of the query point
    * @param y The y location of the query point
    * @return The octant number of the query point.
    */
  // scalastyle:off cyclomatic.complexity
  def getOctant(x: Double, y: Double): Int = {
    if (0.0 == x && 0.0 == y) {
      0
    } else if (x >= 0 && y > 0) {
      if (y > x) 1 else 0
    } else if (x < 0 && y >=0) {
      if (-x > y) 3 else 2
    } else if (x <= 0 && y < 0) {
      if (-y > -x) 5 else 4
    } else { // x > 0 && y <= 0
      if (x > -y) 7 else 6
    }
  }
  // scalastyle:on cyclomatic.complexity


  /**
    * Get the sectors between start and end, assuming a cyclical sector system (like quadrants or octants)
    *
    * @param startSector The first sector
    * @param withinStartSector A relative proportion of the way through the start sector of the start point.  Exact
    *                          proportion doesn't matter, as long as it is the correct direction from withinEndSector
    * @param endSector The last sector
    * @param withinEndSector A relative proportion of the way through the end sector of the end point.  Exact
    *                        proportion doesn't matter, as long as it is the correct direction from withinStartSector
    * @param numSectors The total number of sectors
    * @param positive True if we want the sectors from start to end travelling in the positive direction; false if the
    *                 negative direction.
    * @return All sectors from the first to the last, including both.
    */
  def getInterveningSectors (startSector: Int, withinStartSector: Double,
                             endSector: Int, withinEndSector: Double,
                             numSectors: Int, positive: Boolean): Seq[Int] = {
    if (positive) {   // scalastyle:ignore
      if (endSector > startSector) {
        startSector to endSector
      } else if (endSector < startSector) {
        startSector to (endSector + numSectors)
      } else if (withinStartSector > withinEndSector) {
        startSector to endSector
      } else {
        startSector to (endSector + numSectors)
      }
    } else {
      if (endSector < startSector) {
        startSector to endSector by -1
      } else if (endSector > startSector) {
        startSector to (endSector - numSectors) by -1
      } else if (withinStartSector < withinEndSector) {
        startSector to endSector by -1
      } else {
        startSector to (endSector - numSectors) by -1
      }
    }.map(n => (n + 2 * numSectors) % numSectors)
  }


  /**
    * Computes the slope of the line between points.
    * @param p0 First endpoint.
    * @param p1 Second endpoint.
    * @return The computed slope value.
    */
  def getSlope (p0: DoubleTuple, p1: DoubleTuple): Double = (p0.y - p1.y) / (p0.x - p1.x)


  /**
    * Find the angle of a a given point on a circle around the origin.
    * @param point An (x,y) location to find the angle for
    * @return the angle to the point in radians
    */
  def getCircleAngle (point: DoubleTuple): Double = math.atan2(point.y, point.x)


  /**
    * Essentially number mod modulus, but with the ability to specify the
    * output range ie. solution to x = number % modulus that is closest to base
    *
    * @param base The center of the desired output range
    * @param number The number in question
    * @param modulus The modulus - i.e., the width of the range
    * @return the equivalent of number, mod modulus, centered on the parameter base
    *         (i.e., in the range [base-modulus/2,
    *         base+modulus/2))
    */
  def toClosestModulus (base: Double, number: Double, modulus: Double): Double = {
    val diff = number - base
    val moduli = (diff / modulus).round
    number - moduli * modulus
  }
}


private case class SquareBounds (min: Double, current: Double, max: Double) {
  def asTuple: (Double, Double, Double) = (min, current, max)
}


private case class ArcPointInfo (point: (Int, Int), center: DoubleTuple, slope: Double, octant: Int, inBounds: Boolean,
                         private var xSquared: Option[SquareBounds], private var ySquared: Option[SquareBounds]) {
  def getXSquareBounds: SquareBounds = {
    if (xSquared.isEmpty) {
      val x = point._1
      val x2min = (x - 0.5 - center.x) * (x - 0.5 - center.x)
      val x2max = (x + 0.5 - center.x) * (x + 0.5 - center.x)
      val x2 = (x - center.x) * (x - center.x)
      xSquared = Some(SquareBounds(x2min min x2max, x2, x2min max x2max))
    }
    xSquared.get
  }
  def getYSquareBounds: SquareBounds = {
    if (ySquared.isEmpty) {
      val y = point._2
      val y2min = (y - 0.5 - center.y) * (y - 0.5 - center.y)
      val y2max = (y + 0.5 - center.y) * (y + 0.5 - center.y)
      val y2 = (y - center.y) * (y - center.y)
      ySquared = Some(SquareBounds(y2min min y2max, y2, y2min max y2max))
    }
    ySquared.get
  }
}

/**
  * A class that can report the individual pixels on an arc from start to end of the given arc length
  *
  * @param start The starting point of the arc
  * @param end The ending point of the arc
  * @param arcLength The angular length of the arc, in radians
  * @param clockwise True if the arc moves clockwise, false if counterclockwse
  */
class ArcBinner (start: DoubleTuple, end: DoubleTuple, arcLength: Double, clockwise: Boolean) {

  import ArcBinner._    // scalastyle:ignore
  import DoubleTuple._  // scalastyle:ignore

  private val center = getArcCenter(start, end, arcLength, clockwise)

  private val startOctant = getOctant(start - center)
  private val startSlope = getSlope(start, center)

  private val endOctant = getOctant(end - center)
  private val endSlope = getSlope(end, center)

  // scalastyle:off cyclomatic.complexity
  private val octantList =
    getInterveningSectors(startOctant, startSlope, endOctant, endSlope, 8, !clockwise)

  private def isInBounds (point: DoubleTuple, slope: Double, octant: Int): Boolean = {
    if (startOctant == endOctant) {
      if (octant != startOctant) {
        false
      } else if (clockwise) {
        endSlope <= slope && slope <= startSlope
      } else {
        startSlope <= slope && slope <= endSlope
      }
    } else if (octant == startOctant) {
      if (clockwise) {
        slope <= startSlope
      } else {
        startSlope <= slope
      }
    } else if (octant == endOctant) {
      if (clockwise) {
        endSlope <= slope
      } else {
        slope <= endSlope
      }
    } else if (octantList.contains(octant)) {
      true
    } else {
      false
    }
  }
  // scalastyle:on cyclomatic.complexity

  private def getInBoundsPointInfo (point: DoubleTuple) = {
    val floor = point.floor
    val isInBounds = true
    ArcPointInfo(floor, center, getSlope(floor, center), getOctant(floor - center), isInBounds, None, None)
  }

  private val startPointInfo = getInBoundsPointInfo(start)
  private val endPointInfo = getInBoundsPointInfo(end)

  private var currentPoint: Option[ArcPointInfo] = None
  private var nextPoint: Option[ArcPointInfo] = None
  private var previousPoint: Option[ArcPointInfo] = None

  resetToStart()

  /**
    * Set arc iterator to the start of the sequence of arc pixels.
    */
  def resetToStart(): Unit = {
    currentPoint = Some(startPointInfo)
    nextPoint = Some(startPointInfo)
    previousPoint = None
  }

  /**
    * Set arc iterator to the end of the sequence of arc pixels.
    */
  def resetToEnd(): Unit = {
    currentPoint = Some(endPointInfo)
    nextPoint = None
    previousPoint = Some(endPointInfo)
  }

  private def incrementX (current: ArcPointInfo, positive: Boolean): ArcPointInfo = {
    val sign = if (positive) 1 else -1
    var (x, y) = current.point
    var (y2min, y2, y2max) = current.getYSquareBounds.asTuple

    y2 = y2 - sign * 2.0 * (x - center.x) - 1.0
    x = x + sign

    if (y2 < y2min) {
      if (y - center.y > 0) {
        y = y - 1
        y2max = y2min
        y2min = (y - 0.5 - center.y) * (y - 0.5 - center.y)
      } else {
        y = y + 1
        y2max = y2min
        y2min = (y + 0.5 - center.y) * (y + 0.5 - center.y)
      }
    } else if (y2 >= y2max) {
      if (y - center.y > 0) {
        y = y + 1
        y2min = y2max
        y2max = (y + 0.5 - center.y) * (y + 0.5 - center.y)
      } else {
        y = y - 1
        y2min = y2max
        y2max = (y - 0.5 - center.y) * (y - 0.5 - center.y)
      }
    }

    val point = (x, y)
    val slope = getSlope(point, center)
    val octant = getOctant(point - center)
    val inBounds = isInBounds(point, slope, octant)
    ArcPointInfo((x, y), center, slope, octant, inBounds, None, Some(SquareBounds(y2min, y2, y2max)))
  }

  private def incrementY (current: ArcPointInfo, positive: Boolean): ArcPointInfo = {
    val sign = if (positive) 1 else -1
    var (x, y) = current.point
    var (x2min, x2, x2max) = current.getXSquareBounds.asTuple

    x2 = x2 - sign * 2.0 * (y - center.y) - 1.0
    y = y + sign

    if (x2 < x2min) {
      if (x - center.x > 0) {
        x = x - 1
        x2max = x2min
        x2min = (x - 0.5 - center.x) * (x - 0.5 - center.x)
      } else {
        x = x + 1
        x2max = x2min
        x2min = (x + 0.5 - center.x) * (x + 0.5 - center.x)
      }
    } else if (x2 >= x2max) {
      if (x - center.x > 0) {
        x = x + 1
        x2min = x2max
        x2max = (x + 0.5 - center.x) * (x + 0.5 - center.x)
      } else {
        x = x - 1
        x2min = x2max
        x2max = (x - 0.5 - center.x) * (x - 0.5 - center.x)
      }
    }

    val point = (x, y)
    val slope = getSlope(point, center)
    val octant = getOctant(point - center)
    val inBounds = isInBounds(point, slope, octant)
    ArcPointInfo((x, y), center, slope, octant, inBounds, Some(SquareBounds(x2min min x2max, x2, x2min max x2max)), None)
  }

  private val positive = true
  private val negative = false
  private def nextClockwise(): ArcPointInfo = {
    currentPoint.get.octant match {
      case 7 => incrementY(currentPoint.get, negative)
      case 0 => incrementY(currentPoint.get, negative)
      case 1 => incrementX(currentPoint.get, positive)
      case 2 => incrementX(currentPoint.get, positive)
      case 3 => incrementY(currentPoint.get, positive)
      case 4 => incrementY(currentPoint.get, positive)
      case 5 => incrementX(currentPoint.get, negative)
      case 6 => incrementX(currentPoint.get, negative)
    }
  }

  private def nextCounterclockwise(): ArcPointInfo = {
    currentPoint.get.octant match {
      case 7 => incrementY(currentPoint.get, positive)
      case 0 => incrementY(currentPoint.get, positive)
      case 1 => incrementX(currentPoint.get, negative)
      case 2 => incrementX(currentPoint.get, negative)
      case 3 => incrementY(currentPoint.get, negative)
      case 4 => incrementY(currentPoint.get, negative)
      case 5 => incrementX(currentPoint.get, positive)
      case 6 => incrementX(currentPoint.get, positive)
    }
  }

  /**
    * @return true if there's another pixel in the arc sequence, false if the iteration has reached
    *         the end of the sequence.
    */
  def hasNext: Boolean = {
    if (nextPoint.isEmpty) {
      nextPoint = Some(
        if (clockwise) {
          nextClockwise()
        } else {
          nextCounterclockwise()
        }
      )
    }
    nextPoint.get.inBounds
  }

  /**
    * @return true if there's a previous pixel in the arc sequence, false if the iterator is at the start
    *         of the sequence.
    */
  def hasPrevious: Boolean = {
    if (previousPoint.isEmpty) {
      previousPoint = Some(
        if (clockwise) {
          nextCounterclockwise()
        } else {
          nextClockwise()
        }
      )
    }
    previousPoint.get.inBounds
  }

  /**
    * Moves the iterator to the next pixel in the arc pixel sequence.  Throws an exception
    * if the end of the sequence has already been reached.
    *
    * @return The next pixel in the arc pixel sequence.
    */
  def next(): (Int, Int) = {
    if (hasNext) {
      previousPoint = Some(currentPoint.get)
      currentPoint = Some(nextPoint.get)
      nextPoint = None

      currentPoint.get.point
    } else {
      throw new Exception("Attempt to get past start of arc")
    }
  }

  /**
    * Moves the iterator to the previous pixel in the arc pixel sequence.  Throws an
    * exception if the start of the sequence has already been reached.
    *
    * @return The previous pixel in the arc pixel sequence.
    */
  def previous(): (Int, Int) = {
    if (hasPrevious) {
      nextPoint = Some(currentPoint.get)
      currentPoint = Some(previousPoint.get)
      previousPoint = None

      currentPoint.get.point
    } else {
      throw new Exception("Attempt to get past end of arc")
    }
  }

  /**
    * @return An Iterator over the remaining pixels in the arc pixel sequence.
    */
  def remaining: Iterator[(Int, Int)] = new Iterator[(Int, Int)] {
    override def hasNext: Boolean = ArcBinner.this.hasNext
    override def next(): (Int, Int) = ArcBinner.this.next()
  }

  /**
    * @return An Iterator over the preceding pixels in the arc pixel sequence.
    */
  def preceding: Iterator[(Int, Int)] = new Iterator[(Int, Int)] {
    override def hasNext: Boolean = ArcBinner.this.hasPrevious
    override def next(): (Int, Int) = ArcBinner.this.previous()
  }
}

/**
  * Implicit conversion functions for the DoubleTuple class, to handle conversion to and from Int and Double
  * 2-tuples.
  */
object DoubleTuple {
  implicit def fromTupleOfDouble (tuple: (Double, Double)): DoubleTuple = new DoubleTuple(tuple._1, tuple._2)
  implicit def fromTupleOfInt (tuple: (Int, Int)): DoubleTuple = new DoubleTuple(tuple._1.toDouble, tuple._2.toDouble)
  implicit def toTupleOfDouble (tuple: DoubleTuple): (Double, Double) = (tuple.x, tuple.y)
  implicit def toTupleOfInt (tuple: DoubleTuple): (Int, Int) = (tuple.x.toInt, tuple.y.toInt)
}

// scalastyle:off method.name
/**
  * A 2-tuple of Double values with that supports standard mathematical operations.
  * @param x The x component of the tuple.
  * @param y The y component of the tuple.
  */
case class DoubleTuple (x: Double, y: Double) {
  /**
    * Adds left to right DoubleTuple object.
    */
  def +(that: DoubleTuple): DoubleTuple = DoubleTuple(this.x + that.x, this.y + that.y)

  /**
    * Subtracts right from left DoubleTuple objects.
    */
  def -(that: DoubleTuple): DoubleTuple = DoubleTuple(this.x - that.x, this.y - that.y)

  /**
    * Multiplies left and right DoubleTuple objects.
    */
  def *(that: DoubleTuple): DoubleTuple = DoubleTuple(this.x * that.x, this.y * that.y)

  /**
    * Divides left by right DoubleTuple objects.
    */
  def /(that: DoubleTuple): DoubleTuple = DoubleTuple(this.x / that.x, this.y / that.y)

  /**
    * Divides left tuple object by right value.
    */
  def / (that: Double): DoubleTuple = DoubleTuple(this.x / that, this.y / that)

  /**
    * Computes the length of the DoubleTuple.
    * @return
    */
  def length: Double = math.sqrt(x * x + y * y)

  /**
    * Computes the floor of the DoubleTuple's x and y components.
    */
  def floor: DoubleTuple = DoubleTuple(x.floor, y.floor)

  /**
    * Computes the ceiling of the DoubleTuple's x and y components.
    */
  def ceil: DoubleTuple = DoubleTuple(x.ceil, y.ceil)
}

/**
  * A 2D rotation matrix.
  */
case class DoubleRotation (r00: Double, r01: Double, r10: Double, r11: Double) {
  /**
    * Transforms supplied point by applying the defined rotation matrix.
    *
    * @param v  The point to transform.
    * @return A new rotated point.
    */
  def rotate(v: DoubleTuple): DoubleTuple = this * v

  /**
    * Transforms supplied point by applying the defined rotation matrix.
    *
    * @param v The point to transform.
    * @return A new rotated point.
    */
  def *(v: DoubleTuple): DoubleTuple =
    DoubleTuple(r00 * v.x + r01 * v.y, r10 * v.x + r11 * v.y)
}
// scalastyle:on method.name
