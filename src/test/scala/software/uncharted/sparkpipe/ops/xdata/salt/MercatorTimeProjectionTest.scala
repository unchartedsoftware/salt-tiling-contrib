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
package software.uncharted.sparkpipe.ops.xdata.salt

import org.scalatest.FunSpec
import software.uncharted.salt.xdata.projection.MercatorTimeProjection
import software.uncharted.salt.xdata.util.RangeDescription

// scalastyle:off magic.number
class MercatorTimeProjectionTest extends FunSpec {
  describe("MercatorTimeProjectionTest") {
    describe("#project()") {
      it("should return None when the data space coordinate is none") {
        val proj = new MercatorTimeProjection(Seq(0))
        assertResult(None)(proj.project(None, (32, 32, 32)))
      }

      it("should return None when the time coord is out of range") {
        val proj = new MercatorTimeProjection(Seq(0), RangeDescription.fromCount(0L, 100L, 10))
        assertResult(None)(proj.project(Some(0.0, 0.0, 101L), (32, 32, 32)))
      }

      it("should return None when the geo coords are out of range") {
        val proj = new MercatorTimeProjection(Seq(0), RangeDescription.fromCount(0L, 100L, 10))
        assertResult(None)(proj.project(Some(0.0, MercatorTimeProjection.minLat - 1.0, 10L), (32, 32, 32)))
      }

      it("should assign values to the correct time bucket") {
        val proj = new MercatorTimeProjection(Seq(0), RangeDescription.fromCount(10L, 210L, 10))
        assertResult(Some(List(((0, 0, 0), (50, 50, 2)))))(proj.project(Some(0.0, 0.0, 53L), (100, 100, 10)))
      }
    }

    describe("#binTo1D()") {
      it("should convert a (lon,lat,time) tuple into a linear coordinate") {
        val proj = new MercatorTimeProjection(Seq(0))
        assertResult(8064)(proj.binTo1D((4, 8, 12), (30, 20, 10)))
      }
    }

    describe("#binFrom1D()") {
      it("should convert a linear coordinate into a (lon, lat, time) triple") {
        val proj = new MercatorTimeProjection(Seq(0))
        assertResult((4, 8, 12))(proj.binFrom1D(8064, (30, 20, 10)))
      }
    }
  }
}
