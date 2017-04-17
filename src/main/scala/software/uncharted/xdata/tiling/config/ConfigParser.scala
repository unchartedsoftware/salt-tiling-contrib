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

import scala.collection.JavaConverters._ // scalastyle:ignore
import com.typesafe.config.Config

/**
  * A mix-in trait to with some helper functions for parsing config files
  */
trait ConfigParser {
  private def getOption[T] (config: Config, possibleKeys: String*)(extractFrom: (Config, String) => T): Option[T] = {
    possibleKeys.find(key => config.hasPath(key)).map(key => extractFrom(config, key))
  }

  /**
    * Get a sub-configuration from a configuration, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found.
    * @return The sub-configuration, or None if no named sub-configuration is found
    */
  def getConfigOption (config: Config, possibleKeys: String*): Option[Config] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getConfig(k))
  }
  /**
    * Get the string value of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The String value requested, or None if no such value is found
    */
  def getStringOption (config: Config, possibleKeys: String*): Option[String] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getString(k))
  }
  /**
    * Get the integer value of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The Int value requested, or None if no such value is found
    */
  def getIntOption (config: Config, possibleKeys: String*): Option[Int] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getInt(k))
  }
  /**
    * Get the real-number value of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The Double value requested, or None if no such value is found
    */
  def getDoubleOption (config: Config, possibleKeys: String*): Option[Double] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getDouble(k))
  }
  /**
    * Get the boolean value of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The Boolean value requested, or None if no such value is found
    */
  def getBooleanOption (config: Config, possibleKeys: String*): Option[Boolean] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getBoolean(k))
  }

  /**
    * Get the list of string values of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The String values requested, or an empty list if no such values are found
    */
  def getStringList (config: Config, possibleKeys: String*): Seq[String] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getStringList(k).asScala.toSeq)
      .getOrElse(Seq[String]())
  }
  /**
    * Get the list of integer values of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The Int values requested, or an empty list if no such values are found
    */
  def getIntList (config: Config, possibleKeys: String*): Seq[Int] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getIntList(k).asScala.toSeq.map(_.intValue()))
      .getOrElse(Seq[Int]())
  }
  /**
    * Get the list of real-number values of a configuration key, if it is present
    * @param config The root configuration to search
    * @param possibleKeys A list of possible keys to search; they will be searched in order until one valid result
    *                     is found
    * @return The Double values requested, or an empty list if no such values are found
    */
  def getDoubleList (config: Config, possibleKeys: String*): Seq[Double] = {
    getOption(config, possibleKeys:_*)((c, k) => c.getDoubleList(k).asScala.toSeq.map(_.doubleValue()))
      .getOrElse(Seq[Double]())
  }

  private def getDefaulted[T] (config: Config, key: String, extractFrom: Config => T, defaultValue: T): T = {
    if (config.hasPath(key)) {
      extractFrom(config)
    } else {
      defaultValue
    }
  }

  /**
    * Get the string value of a configuration key, if it is present; if it is not present, use the default value
    * given instead
    * @param config The root configuration to search
    * @param key The configuration key for which to search
    * @param defaultValue The value to use if the configuration key isn't found
    * @return the appropriate value, as specified, to use for this configuration key
    */
  def getString (config: Config, key: String, defaultValue: String): String = {
    getDefaulted(config, key, _.getString(key), defaultValue)
  }
  /**
    * Get the integer value of a configuration key, if it is present; if it is not present, use the default value
    * given instead
    * @param config The root configuration to search
    * @param key The configuration key for which to search
    * @param defaultValue The value to use if the configuration key isn't found
    * @return the appropriate value, as specified, to use for this configuration key
    */
  def getInt (config: Config, key: String, defaultValue: Int): Int = {
    getDefaulted(config, key, _.getInt(key), defaultValue)
  }
  /**
    * Get the real-number value of a configuration key, if it is present; if it is not present, use the default value
    * given instead
    * @param config The root configuration to search
    * @param key The configuration key for which to search
    * @param defaultValue The value to use if the configuration key isn't found
    * @return the appropriate value, as specified, to use for this configuration key
    */
  def getDouble (config: Config, key: String, defaultValue: Double): Double = {
    getDefaulted(config, key, _.getDouble(key), defaultValue)
  }
  /**
    * Get the boolean value of a configuration key, if it is present; if it is not present, use the default value
    * given instead
    * @param config The root configuration to search
    * @param key The configuration key for which to search
    * @param defaultValue The value to use if the configuration key isn't found
    * @return the appropriate value, as specified, to use for this configuration key
    */
  def getBoolean (config: Config, key: String, defaultValue: Boolean): Boolean = {
    getDefaulted(config, key, _.getBoolean(key), defaultValue)
  }
}
