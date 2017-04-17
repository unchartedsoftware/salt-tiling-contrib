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

package software.uncharted.salt.xdata.projection

import software.uncharted.salt.core.projection.numeric.CartesianProjection
import software.uncharted.salt.xdata.util.RangeDescription

class CartesianTimeProjection(zoomLevels: Seq[Int],
                              min: (Double, Double, Long) = (0.0, 0.0, 0),
                              max: (Double, Double, Long) = (1.0, 1.0, Long.MaxValue),
                              rangeBuckets: Long = Long.MaxValue)
  extends XYTimeProjection(min, max, rangeBuckets, new CartesianProjection(zoomLevels, (min._1, min._2), (max._1, max._2))) {

  def this(zoomLevels: Seq[Int], min: (Double, Double), max: (Double, Double), timeRange: RangeDescription[Long]) = {
    this(zoomLevels, (min._1, min._2, timeRange.min), (max._1, max._2, timeRange.max), timeRange.count)
  }
}
