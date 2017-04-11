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
package software.uncharted.salt.xdata.projection.geometry

import org.scalatest.FunSuite
import org.scalatest.Matchers._


class LineTests extends FunSuite {
  private val epsilon = 1E-12

  test("Line equality") {
    assert(Line(2, 2, 2) == Line(1, 1, 1))
  }

  test("Line construction from points") {
    for (x0 <- -5 to 5; y0 <- -5 to 5; x1 <- -5 to 5; y1 <- -5 to 5) {
      if (x0 == x1 && y0 == y1) {
        intercept[IllegalArgumentException] {
          Line((x0, y0), (x1, y1))
        }
      } else {
        val message = "Line [%d, %d] => [%d, %d]".format(x0, y0, x1, y1)
        val line = Line((x0, y0), (x1, y1))
        if (y0 != y1) {
          line.xOf(y0) should be(x0 * 1.0 +- epsilon)
          line.xOf(y1) should be(x1 * 1.0 +- epsilon)
          line.xOf((y0 + y1) / 2.0) should be((x0 + x1) / 2.0 +- epsilon)
        }
        if (x0 != x1) {
          line.yOf(x0) should be(y0 * 1.0 +- epsilon)
          line.yOf(x1) should be(y1 * 1.0 +- epsilon)
          line.yOf((x0 + x1) / 2.0) should be((y0 + y1) / 2.0 +- epsilon)
        }
      }
    }
  }

  test("Intersection") {
    (Line(1, 2, 3) intersection Line(1, 3, 5)) should be (-1.0, 2.0)
    (Line(0, 1, 5) intersection Line(1, 0, 3)) should be (3.0, 5.0)
    (Line(0, 1, 0) intersection Line(5, 2, 3)) should be (0.6, 0.0)
    (Line(1, 0, 0) intersection Line(4, 10, 7)) should be (0.0, 0.7)
  }

  test("distance") {
    Line(1, 0, 13).distanceTo(21.0, -4.0) should be (8.0 +- epsilon)
    Line(1, 0, 13).distanceTo( 5.0, -4.0) should be (8.0 +- epsilon)
    Line(0, 1, -3).distanceTo(21.0, -4.0) should be (1.0 +- epsilon)
    Line(0, 1, -3).distanceTo(21.0, -2.0) should be (1.0 +- epsilon)
    Line(1, 2, 10).distanceTo( 0.0,  0.0) should be (math.sqrt(20) +- epsilon)
  }
}
