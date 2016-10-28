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

import software.uncharted.xdata.ops.util.BasicOperations._
import software.uncharted.xdata.ops.salt.BasicSaltOperations._
import software.uncharted.xdata.spark.SparkFunSpec
import software.uncharted.xdata.ops.{numeric => XDataNum}



class BasicSaltOperationsTest extends SparkFunSpec {
  describe("BasicSaltOperations") {
    describe("#getBounds") {
      it("should return the proper bounds of a set of coordinates") {
        val data = toDataFrame(sparkSession)(sc.parallelize(Seq(
          Coordinates(0.0, 0.0, 0.0, 0.0),
          Coordinates(1.0, 4.0, 3.0, 5.0),
          Coordinates(2.0, 2.0, 1.0, 0.0),
          Coordinates(-1.0, -2.0, -3.0, -4.0)
        )))

        assert(List((-1.0, 2.0), (-2.0, 4.0)) === getBounds("w", "x")(data).toList)
        assert(List((-3.0, 3.0), (-4.0, 5.0)) === getBounds("y", "z")(data).toList)
        assert(List((-2.0, 4.0), (-3.0, 3.0)) === getBounds("x", "y")(data).toList)
      }
    }

    describe("#cartesianTiling") {
      it("should properly tile without autobounds") {
        val data = toDataFrame(sparkSession)(sc.parallelize(Seq(
          Coordinates(0.0,-1.0, 0.0, 0.0),
          Coordinates(0.0, 0.0, -1.0, 0.0),
          Coordinates(0.0, 0.0, 0.0, 0.0),
          Coordinates(0.0, 0.5, 0.5, 0.0),
          Coordinates(0.0, 1.5, 3.5, 0.0),
          Coordinates(0.0, 2.5, 2.5, 0.0),
          Coordinates(0.0, 3.5, 1.5, 0.0),
          Coordinates(0.0, 4.0, 0.0, 0.0),
          Coordinates(0.0, 0.0, 4.0, 0.0)
        )))
        val tiles = cartesianTiling("x", "y", "count", Seq(0), Some((0.0, 0.0, 4.0, 4.0)), 4)(XDataNum.addConstantColumn("count", 1)(data)).collect

        assert(List(0.0, 1.0, 0.0, 0.0,  0.0, 0.0, 1.0, 0.0,  0.0, 0.0, 0.0, 1.0,  2.0, 0.0, 0.0, 0.0) === tiles(0).bins.seq.toList)
      }

      it("should properly tile with autobounds") {
        val data = toDataFrame(sparkSession)(sc.parallelize(Seq(
          Coordinates(0.0, 0.0, 0.0, 0.0),
          Coordinates(0.0, 0.5, 0.5, 0.0),
          Coordinates(0.0, 1.5, 3.5, 0.0),
          Coordinates(0.0, 2.5, 2.5, 0.0),
          Coordinates(0.0, 3.5, 1.5, 0.0),
          Coordinates(0.0, 4.0, 4.0, 0.0)
        )))
        val tiles = cartesianTiling("x", "y", "count", Seq(0), None, 4)(XDataNum.addConstantColumn("count", 1)(data)).collect

        assert(List(0.0, 1.0, 0.0, 1.0,  0.0, 0.0, 1.0, 0.0,  0.0, 0.0, 0.0, 1.0,  2.0, 0.0, 0.0, 0.0) === tiles(0).bins.seq.toList)
      }
    }
  }
}
case class Coordinates (w: Double, x: Double, y: Double, z: Double)
