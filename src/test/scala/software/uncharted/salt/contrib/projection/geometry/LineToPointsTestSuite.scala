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

package software.uncharted.salt.contrib.projection.geometry

import scala.util.Try
import org.scalatest.FunSuite

class LineToPointsTestSuite extends FunSuite {
  test("get all points") {
    val l2p = new LineToPoints((4, 2), (20, 8))

    assert(17 === l2p.available)
    val points = l2p.rest()
    assert(points.length === 17)
    assert((4, 2) === points(0))
    assert((20, 8) === points(16))

    points.sliding(2).foreach { p =>
      assert(p(0)._1 === p(1)._1 - 1)
      assert(p(0)._2 == p(1)._2 || p(0)._2 == p(1)._2 - 1)
    }
  }

  test("Reversing input order should reverse output order") {
    val l2pF = new LineToPoints((4, 2), (20, 8))
    val pointsF = l2pF.rest().toList

    val l2pR = new LineToPoints((20, 8), (4, 2))
    val pointsR = l2pR.rest().toList

    assert(pointsF === pointsR.reverse)
  }

  test("Very short lines") {
    for (x0 <- -2 to 2; y0 <- -2 to 2; x1 <- -2 to 2; y1 <- -2 to 2) {
      if (x0 != x1 || y0 != y1) {
        val msg = "Line: [%d, %d] => [%d, %d]".format(x0, y0, x1, y1)

        val l2p = new LineToPoints((x0, y0), (x1, y1))
        val points = l2p.rest().toList
        assert((x0, y0) === points.head, msg)
        assert((x1, y1) === points.last, msg)

        val l2pr = new LineToPoints((x1, y1), (x0, y0))
        assert(points === l2pr.rest().toList.reverse, msg)
      }
    }
  }

  test("reset") {
    val l2p = new LineToPoints((4, 2), (20, 8))
    val points1 = l2p.rest().toList
    l2p.reset()
    val points2 = l2p.rest().toList

    assert(points1 === points2)
  }

  test("get some points") {
    def testNth (n: Int): Unit = {
      val l2p = new LineToPoints((4, 2), (20, 8))
      val points1 = l2p.rest().toList.grouped(n).flatMap(group => Try(group(n-1)).toOption).toList
      l2p.reset()
      val points2 = (4 to 20).grouped(n).flatMap(group => Try(group(n-1)).toOption)
        .map(i => l2p.skip(n - 1)).toList

      assert(points1 === points2, "skipping "+n)
    }

    (2 to 16).map(testNth)
  }

  test("get some points for reversed line") {
    def testNth (n: Int): Unit = {
      val l2p = new LineToPoints((20, 8), (4, 2))
      val points1 = l2p.rest().toList.grouped(n).flatMap(group => Try(group(n-1)).toOption).toList
      l2p.reset()
      val points2 = (20 to 4 by -1).grouped(n).flatMap(group => Try(group(n-1)).toOption)
        .map(i => l2p.skip(n - 1)).toList

      assert(points1 === points2, "skipping "+n)
    }

    (2 to 16).map(testNth)
  }

  test("skip to point") {
    val l2p = new LineToPoints((4, 2), (20, 8))
    val reference = l2p.rest().toMap
    l2p.reset()

    assert((8, reference(8)) === l2p.skipTo(8))
    assert((10, reference(10)) === l2p.skipTo(10))
    assert((11, reference(11)) === l2p.skipTo(11))
    assert((13, reference(13)) === l2p.skipTo(13))
    assert((14, reference(14)) === l2p.skipTo(14))
  }

  test("Skip to point, horizontal line") {
    val l2p = new LineToPoints((0, 0), (22, 0))

    for (i <- 0 to 22) {
      assert((i, 0) === l2p.skipTo(i))
      l2p.reset()
    }
  }

  test("skip to point, vertical line") {
    val l2p = new LineToPoints((0, 0), (0, 22))

    for (i <- 0 to 22) {
      assert((0, i) === l2p.skipTo(i))
      l2p.reset()
    }
  }

  test("skip to point with reversed line") {
    val l2p = new LineToPoints((20, 8), (4, 2))
    val reference = l2p.rest().toMap
    l2p.reset()

    assert((14, reference(14)) === l2p.skipTo(14))
    assert((13, reference(13)) === l2p.skipTo(13))
    assert((11, reference(11)) === l2p.skipTo(11))
    assert((10, reference(10)) === l2p.skipTo(10))
    assert((8, reference(8)) === l2p.skipTo(8))
  }

  test("skip to distance from point") {
    val l2p = new LineToPoints((4, 2), (20, 8))
    val reference = l2p.rest().toMap
    l2p.reset()

    intercept[NoIntersectionException] {
      l2p.skipToDistance((-40, -40), 5)
    }

    intercept[NoIntersectionException] {
      l2p.skipToDistance((-40, 40), 5)
    }

    intercept[NoIntersectionException] {
      l2p.skipToDistance((40, -40), 5)
    }

    intercept[NoIntersectionException] {
      l2p.skipToDistance((40, 40), 5)
    }

    assert((7, reference(7)) === l2p.skipToDistance((10, 10), 7))
    assert((16, reference(16)) === l2p.skipToDistance((10, 10), 7))
    intercept[NoIntersectionException] {
      l2p.skipToDistance((10, 10), 7)
    }
  }

  test("skip to distance from point 2") {
    val l2p = new LineToPoints((20, 8), (4, 2))
    val reference = l2p.rest().toMap
    l2p.reset()

    intercept[NoIntersectionException] {
      l2p.skipToDistance((-40, -40), 5)
    }

    intercept[NoIntersectionException] {
      l2p.skipToDistance((-40, 40), 5)
    }

    intercept[NoIntersectionException] {
      l2p.skipToDistance((40, -40), 5)
    }

    intercept[NoIntersectionException] {
      l2p.skipToDistance((40, 40), 5)
    }

    // Going down, it errs on the other side of each boundary
    assert((17, reference(17)) === l2p.skipToDistance((10, 10), 7))
    assert((8, reference(8)) === l2p.skipToDistance((10, 10), 7))
    intercept[NoIntersectionException] {
      l2p.skipToDistance((10, 10), 7)
    }
  }

  test("NoIntersectionException constructor") {
    val noArgResult = new NoIntersectionException()

    assert(noArgResult.getMessage == null)
    assert(noArgResult.getCause == null)

    val msg = "this is a message"
    val messageResult = new NoIntersectionException(msg)

    assert(messageResult.getMessage == msg)
    assert(messageResult.getCause == null)

    val cause = new Throwable("this is the cause of the exception")
    val throwableResult = new NoIntersectionException(cause)

    assert(throwableResult.getMessage == null)
    assert(throwableResult.getCause == cause)
  }

  test("Various utility functions") {
    val l2p = new LineToPoints((-1, 1), (1, 1))
    val l2p_2 = new LineToPoints((0, -2), (1, 0))

    assert(l2p.curLongAxisPosition == -1)

    assert(l2p.lastLongAxisPosition == 1)

    assert(l2p.curLongAxisStartDistance == 0)

    assert(l2p.curLongAxisEndDistance == 2)

    assert(l2p.curStartDistance == 0)

    assert(l2p.curEndDistance == 2)

    assert(l2p.longAxisValue(1, 2) == 1)
    assert(l2p_2.longAxisValue(1, 2) == 2)

    assert(l2p.shortAxisValue(1, 2) == 2)

    assert(l2p.hasNext == true)

    assert(l2p.available == 3)

    assert(l2p.current == (-1, 1))

    assert(l2p.increasing == true)
  }

  test("toDistance") {
    var l2p = new LineToPoints((-1, 1), (1, 1))
    var lineIterator = l2p.toDistance((-1, 1), 1)

    assert(lineIterator.length == 2)

    l2p = new LineToPoints((-1, 1), (1, 1))
    lineIterator = l2p.toDistance((-1, 1), 1)

    assert(lineIterator.hasNext == true)
    assert(lineIterator.next() == (-1,1))
    assert(lineIterator.next() == (0,1))
    assert(lineIterator.next() == (1, 1))
    assert(lineIterator.hasNext == false)

    l2p = new LineToPoints((0, -2), (1, 0))
    lineIterator = l2p.toDistance((0, -2), 1)

    assert(lineIterator.next() === (0, -2))
    assert(lineIterator.hasNext === true)
  }

}
