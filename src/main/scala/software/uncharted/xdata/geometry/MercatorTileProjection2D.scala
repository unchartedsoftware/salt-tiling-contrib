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

package software.uncharted.xdata.geometry

import software.uncharted.salt.core.projection.Projection

trait MercatorBinning {
  /**
    * Specify whether the Y axis is flipped for tile coordinates
    */
  protected def tms: Boolean

  /**
    * Change from a (tile, bin) coordinate to a (universal bin) coordinate
    *
    * Generally, the upper left corner is taken as (0, 0).  If TMS is specified, then the tile Y coordinate is
    * flipped (i.e., lower left is (0, 0)), but the bin coordinates (both tile-bin and universal-bin) are not.
    *
    * @param tile   the tile coordinate
    * @param bin    the bin coordinate
    * @param maxBin the maximum bin index within each tile
    * @return The universal bin coordinate of the target cell, with (0, 0) being the upper left corner of the whole
    *         space
    */
  private def tileBinIndexToUniversalBinIndex(tile: (Int, Int, Int), bin: (Int, Int), maxBin: (Int, Int)): (Int, Int) = {
    val pow2 = 1 << tile._1

    val tileLeft = tile._2 * (maxBin._1 + 1)

    val tileTop = tms match {
      case true => (pow2 - tile._3 - 1) * (maxBin._2 + 1)
      case false => tile._3 * (maxBin._2 + 1)
    }

    (tileLeft + bin._1, tileTop + bin._2)
  }

  /**
    * Change from a (universal bin) coordinate to a (tile, bin) coordinate.
    *
    * Generally, the upper left corner is taken as (0, 0).  If TMS is specified, then the tile Y coordinate is
    * flipped (i.e., lower left is (0, 0)), but the bin coordinates (both tile-bin and universal-bin) are not.
    *
    * @param z            The zoom level of the point in question
    * @param ubin The universal bin coordinate of the input point
    * @param maxBin       the maximum bin index within each tile
    * @return The tile and bin at the given level of the given universal bin
    */
  private def universalBinIndexToTileIndex(z: Int, ubin: (Int, Int), maxBin: (Int, Int)) = {
    val pow2 = 1 << z

    val xBins = (maxBin._1 + 1)
    val yBins = (maxBin._2 + 1)

    val tileX = ubin._1 / xBins
    val binX = ubin._1 - tileX * xBins;

    val tileY = tms match {
      case true => pow2 - (ubin._2 / yBins) - 1;
      case false => ubin._2 / yBins
    }
    val binY = tms match {
      case true => ubin._2 - ((pow2 - tileY - 1) * yBins)
      case false => ubin._2 - (tileY) * yBins
    }

    ((z, tileX, tileY), (binX, binY))
  }
}

abstract class MercatorTileProjection2D[DC, BC](min: (Double, Double), max: (Double, Double), _tms: Boolean)
  extends Projection[DC, (Int, Int, Int), BC] with CartesianBinning {
  assert(max._1 > min._1)
  assert(max._2 > min._2)

  override def tms: Boolean = _tms
}
