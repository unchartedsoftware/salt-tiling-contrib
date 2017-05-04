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

package software.uncharted.xdata.tiling.config

import com.typesafe.config.Config

import scala.util.Try

/**
  * Configuration specifying how term frequency based tiling is to be performed.
  *
  * @param xColumn The dataframe column storing the X data
  * @param yColumn The dataframe column storing the Y data
  * @param textColumn The dataframe column storing the text from which to generate the topics
  * @param projectionConfig The projection from data space to (tile, bin) space.
  */
case class TileTopicConfig(xColumn: String,
                           yColumn: String,
                           textColumn: String,
                           projectionConfig: ProjectionConfig)

/**
  * Provides functions for parsing topic tile data out of `com.typesafe.config.Config` objects.
  *
  * Valid properties are:
  *
  *   - `xColumn` - The assigned name of the column containing the X values
  *   - `yColumn` - The assigned name of the column containing the Y values
  *   - `textColumn` - The assigned name of column containing the text to generate term frequencies from.
  *   - `projection` - One of `cartesian` or `mercator`
  *   - `xyBounds` - Projection bounds as [minX, minY, maxX, maxY].  Points outside of these bounds will be
  *                  ignored.  This value is OPTIONAL for `mercator`, but required for `cartesian`.
  *
  *  Example from config file (in [[https://github.com/typesafehub/config#using-hocon-the-json-superset HOCON]] notation):
  *
  *  {{{
  *  topics {
  *    xColumn = x_vals
  *    yColumn = y_vals
  *    textColumn = text
  *    projection = cartesian
  *    xyBounds = [0.0, 0.0, 100.0, 300.0]
  *  }
  *  }}}
  *
  */
object TileTopicConfig extends ConfigParser {
  private val SectionKey = "topics"
  private val XColumnKey = "xColumn"
  private val YColumnKey = "yColumn"
  private val TextColumnKey = "textColumn"

  /**
    * Parse general tiling parameters out of a config container and instantiates a `TileTopicConfig`
    * object from them.
    *
    * @param config The configuration container.
    * @return A `Try` containing the `TileTopicConfig` object.
    */
  def parse (config: Config): Try[TileTopicConfig] = {
    for (
      section <- Try(config.getConfig(SectionKey));
      projection <- ProjectionConfig.parse(section)
    ) yield {
      TileTopicConfig(
        section.getString(XColumnKey),
        section.getString(YColumnKey),
        section.getString(TextColumnKey),
        projection
      )
    }
  }
}
