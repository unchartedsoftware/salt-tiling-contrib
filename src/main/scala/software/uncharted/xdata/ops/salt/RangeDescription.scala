/**
 * Copyright (c) 2014-2015 Uncharted Software Inc. All rights reserved.
 *
 * Property of Uncharted(tm), formerly Oculus Info Inc.
 * http://uncharted.software/
 *
 * This software is the confidential and proprietary information of
 * Uncharted Software Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Uncharted Software Inc.
 */
package software.uncharted.xdata.ops.salt

import spire.math.Numeric
import spire.implicits._

object RangeDescription {
  def fromMin[T : Numeric](min: T, step: T, count: Int): RangeDescription[T] = RangeDescription(min, step*count, count, step)
  def fromMax[T : Numeric](max: T, step: T, count: Int): RangeDescription[T] = RangeDescription(max - (step*count), max, count, step)
  def fromCount[T : Numeric](min: T, max: T, count: Int): RangeDescription[T] = RangeDescription(min, max, count, (max - min) / count)
  def fromStep[T : Numeric](min: T, max: T, step: T): RangeDescription[T] = RangeDescription(min, max, ((max - min) / step).toInt(), step)
}

case class RangeDescription[T: Numeric](min: T, max: T, count: Int, step: T) {
  require(min < max, "min must be less than max")
  require(count >= 1, "count must be greater than 1")
  require(step > 0, "step must be greater than 0")
}
