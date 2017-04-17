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

case class TileTopicConfig(xColumn: String,
                           yColumn: String,
                           textColumn: String,
                           projectionConfig: ProjectionConfig)
object TileTopicConfig extends ConfigParser {
  private val SECTION_KEY = "topics"
  private val X_COLUMN_KEY = "xColumn"
  private val Y_COLUMN_KEY = "yColumn"
  private val TEXT_COLUMN_KEY = "textColumn"

  def parse (config: Config): Try[TileTopicConfig] = {
    for (
      section <- Try(config.getConfig(SECTION_KEY));
      projection <- ProjectionConfig.parse(section)
    ) yield {
      TileTopicConfig(
        section.getString(X_COLUMN_KEY),
        section.getString(Y_COLUMN_KEY),
        section.getString(TEXT_COLUMN_KEY),
        projection
      )
    }
  }
}
