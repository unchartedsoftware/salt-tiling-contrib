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

package software.uncharted.sparkpipe.ops.xdata.salt

import software.uncharted.sparkpipe.ops.xdata.io.intScoreListToByteArray
import software.uncharted.xdata.spark.SparkFunSpec

case class CartesianTopicTestData(x: Double, y: Double, text: List[String])

class CartesianTopicsTest extends SparkFunSpec {
  private val xCol = "x"
  private val yCol = "y"
  private val textCol = "text"

  it ("should verify contents of tiles") {
    val session = sparkSession
    import session.implicits._ // scalastyle:ignore

    val testData =
      List(CartesianTopicTestData(-99.0, 15.0, List("c", "c", "d", "d", "c", "c")),
        CartesianTopicTestData(-99.0, 40.0, List("a", "a", "a", "a", "a", "a")),
        CartesianTopicTestData(-99.0, 10, List("b", "b", "b", "b", "c", "a")),
        CartesianTopicTestData(95.0, -70.0, List("a", "a", "a", "a", "a", "a")))

    val generatedData = sc.parallelize(testData).toDF()

    val topicsOp = CartesianTopics(
      xCol,
      yCol,
      textCol,
      Some(-100.0, -80.0, 100.0, 80.0),
      10,
      0 until 3,
      4)(_)

    val opsResult = topicsOp(generatedData)

    val coordsResult = opsResult.map(_.coords).collect().toSet
    val expectedCoords = Set(
      (0, 0, 0), // l0
      (1, 1, 0), (1, 0, 1), // l1
      (2, 3, 0), (2, 0, 2), (2, 0, 3)) // l2
    assertResult((Set(), Set()))((expectedCoords diff coordsResult, coordsResult diff expectedCoords))

    val binValues = opsResult.map(elem => (elem.coords, elem.bins)).collect()
    val binCoords = binValues.map {
      elem => (elem._1, new String(intScoreListToByteArray(elem._2).toArray))
    }
    val selectedBinValue = binCoords.filter { input =>
      input._1 match {
        case Tuple3(2,0,2) => true
        case _ => false
      }
    }
    val binValCheck = Array(
      (Tuple3(2,0,2), """[{"binIndex": 8, "topics": {"c": 5, "b": 4, "d": 2, "a": 1}}]""")
    )
    assertResult(binValCheck)(selectedBinValue)
  }
}
