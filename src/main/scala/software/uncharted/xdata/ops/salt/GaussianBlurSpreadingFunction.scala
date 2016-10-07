package software.uncharted.xdata.ops.salt

import software.uncharted.salt.core.spreading.SpreadingFunction
import software.uncharted.xdata.geometry.CartesianBinning

class GaussianBlurSpreadingFunction(radius: Int, sigma: Double, maxBins: (Int, Int))
  extends SpreadingFunction[(Int, Int, Int), (Int, Int), Double] {

  private val kernel = GaussianBlurSpreadingFunction.makeGaussianKernel(radius, sigma)
  private val kernelDimension = GaussianBlurSpreadingFunction.calcKernelDimension(radius)

  /**
    * Spread a single value over multiple visualization-space coordinates
    *
    * @param coordsTraversable the visualization-space coordinates
    * @param value             the value to spread
    * @return Seq[(TC, BC, Option[T])] A sequence of tile coordinates, with the spread values
    */
  // TODO: Are there types for tile coordinate, bin coordinate?
  // TODO: Should those types take into acct 3 dimensional bin coordinates which are present in
  override def spread(coordsTraversable: Traversable[((Int, Int, Int), (Int, Int))], value: Option[Double]): Traversable[((Int, Int, Int), (Int, Int), Option[Double])] = {
    // TODO: Confirm that the default value is 1.0
    val coordsValueMap = coordsTraversable
      .flatMap(addNeighbouringBins(value.getOrElse(1.0))) // Add bins in neighborhood, affected by gaussian blur
      .groupBy(coordsValueMap => coordsValueMap._1) // Group by key. Key: (tileCoordinate, binCoordinate)
      .map({ case (group, traversable) => traversable.reduce { (a, b) => (a._1, a._2 + b._2) } }) // Reduces by key, adding the values

    // TODO: Can I think of a better way to write this
    coordsValueMap.map(applyKernel(coordsValueMap))
  }

  def addNeighbouringBins(value: Double)
                         (coords: ((Int, Int, Int), (Int, Int))): Map[((Int, Int, Int), (Int, Int)), Double] = {
    val tileCoordinate = coords._1
    val binCoordinate = coords._2
    var result = Map(((tileCoordinate, binCoordinate) -> value))

    // Translate kernel coordinates into tile and bin coordinates
    for (kernelX <- 0 to kernelDimension - 1) {
      for (kernelY <- 0 to kernelDimension - 1) {
        val (kernelTileCoord, kernelBinCoord) = calcKernelCoord(tileCoordinate, binCoordinate, (kernelX, kernelY))
        if (kernelTileCoord != tileCoordinate || kernelBinCoord != binCoordinate) {
          result = result + ((kernelTileCoord, kernelBinCoord) -> 0.0)
        }
      }
    }

    result
  }

  // TODO: Can I think of a better name for coordsValueMap (the iterable) and coordsValue (the tuple?)
  def applyKernel(coordsValueMap: Map[((Int, Int, Int), (Int, Int)), Double])
                 (coordsValue: (((Int, Int, Int), (Int, Int)), Double)): ((Int, Int, Int), (Int, Int), Option[Double]) = {
    val coords = coordsValue._1
    val value = coordsValue._2
    val tileCoordinate = coords._1
    val binCoordinate = coords._2
    var result = List[Double]()

    for (kernelX <- 0 to kernelDimension - 1) {
      for (kernelY <- 0 to kernelDimension - 1) {
        val (kernelTileCoord, kernelBinCoord) = calcKernelCoord(tileCoordinate, binCoordinate, (kernelX, kernelY))
        val valueAtCoord = coordsValueMap.get((kernelTileCoord, kernelBinCoord))
        result = result :+ kernel(kernelX)(kernelY) * valueAtCoord.getOrElse(0.0)
      }
    }

    (tileCoordinate, binCoordinate, Some(result.sum))
  }

  def calcKernelCoord(tileCoord: (Int, Int, Int), binCoord: (Int, Int), kernelIndex: (Int, Int)): ((Int, Int, Int), (Int, Int)) = {
    var kernelBinCoord = (binCoord._1 + kernelIndex._1 - Math.floorDiv(kernelDimension, 2), binCoord._2 + kernelIndex._2 - Math.floorDiv(kernelDimension, 2))
    var kernelTileCoord = tileCoord

    // If kernel bin coordinate lies outside of the tile, calculate new coordinates for tile and bin
    if (kernelBinCoord._1 < 0) {
      kernelTileCoord = translateLeft(kernelTileCoord)
      kernelBinCoord = calcBinCoordInLeftTile(kernelBinCoord)
    } else if (kernelBinCoord._1 > maxBins._1) {
      kernelTileCoord = translateRight(kernelTileCoord)
      kernelBinCoord = calcBinCoordInRightTile(kernelBinCoord)
    }

    if (kernelBinCoord._2 < 0) {
      kernelTileCoord = translateUp(kernelTileCoord)
      kernelBinCoord = calcBinCoordInTopTile(kernelBinCoord)
    } else if (kernelBinCoord._2 > maxBins._2) {
      kernelTileCoord = translateDown(kernelTileCoord)
      kernelBinCoord = calcBinCoordInBottomTile(kernelBinCoord)
    }

    (kernelTileCoord, kernelBinCoord)
  }

  // TODO: Consider, will tms need to be taken into acct here?
  def translateLeft(tileCoordinate: (Int, Int, Int)) = (tileCoordinate._1 - 1, tileCoordinate._2, tileCoordinate._3)

  def translateRight(tileCoordinate: (Int, Int, Int)) = (tileCoordinate._1 + 1, tileCoordinate._2, tileCoordinate._3)

  def translateUp(tileCoordinate: (Int, Int, Int)) = (tileCoordinate._1, tileCoordinate._2 + 1, tileCoordinate._3)

  def translateDown(tileCoordinate: (Int, Int, Int)) = (tileCoordinate._1, tileCoordinate._2 - 1, tileCoordinate._3)

  def calcBinCoordInLeftTile(kernelBinCoord: (Int, Int)) = (maxBins._1 + kernelBinCoord._1 + 1, kernelBinCoord._2)

  def calcBinCoordInRightTile(kernelBinCoord: (Int, Int)) = (kernelBinCoord._1 - maxBins._1 - 1, kernelBinCoord._2)

  def calcBinCoordInTopTile(kernelBinCoord: (Int, Int)) = (kernelBinCoord._1, maxBins._1 + kernelBinCoord._2 + 1)

  def calcBinCoordInBottomTile(kernelBinCoord: (Int, Int)) = (kernelBinCoord._1, kernelBinCoord._2 - maxBins._1 - 1)
}

object GaussianBlurSpreadingFunction {
  def makeGaussianKernel(radius: Int, sigma: Double): Array[Array[Double]] = {
    val kernelDimension = calcKernelDimension(radius)
    val kernel = Array.ofDim[Double](kernelDimension, kernelDimension)
    var sum = 0.0

    for (u <- 0 until kernelDimension) {
      for (v <- 0 until kernelDimension) {
        val uc = u - (kernel.length - 1) / 2
        val vc = v - (kernel(0).length - 1) / 2
        // Calculate and save
        val g = Math.exp(-(uc * uc + vc * vc) / (2 * sigma * sigma))
        sum += g
        kernel(u)(v) = g
      }
    }

    // Normalize the kernel
    for (u <- 0 until kernel.length) {
      for (v <- 0 until kernel(0).length) {
        kernel(u)(v) /= sum
      }
    }
    kernel
  }

  def calcKernelDimension(radius: Int) = 2 * radius + 1

  def main(args: Array[String]): Unit = {
    val spreadingFunction = new GaussianBlurSpreadingFunction(4, 3.0, (255, 255))
    val kernel = makeGaussianKernel(4, 3.0)
    val coordMap = spreadingFunction.spread(List(
      ((1, 1, 1), (150, 151)),
      ((1, 1, 1), (150, 152)),
      ((1, 1, 1), (150, 153)),
      ((1, 1, 1), (150, 154)),
      ((1, 1, 1), (150, 155)),
      ((1, 1, 1), (150, 156)),
      ((1, 1, 1), (150, 157)),
      ((1, 1, 1), (150, 158)),
      ((1, 1, 1), (150, 159)),
      ((1, 1, 1), (150, 160)),
      ((1, 1, 1), (150, 161)),
      ((1, 1, 1), (150, 162)),
      ((1, 1, 1), (150, 163)),
      ((1, 1, 1), (150, 164))
    ), Some(1.0))

    coordMap.toList.sortBy((coord) => (coord._2._2, coord._2._1)).foreach(println)
  }
}
