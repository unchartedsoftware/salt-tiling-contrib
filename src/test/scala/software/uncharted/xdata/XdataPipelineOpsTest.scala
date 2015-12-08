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
package software.uncharted.xdata

import org.scalatest.FunSuite

class XdataPipelineOpsTest extends FunSuite {

	test("Test xdata pipeline ops foo") {
		assertResult(0)(XdataPipelineOps.foo())
	}

  test("Test xdata pipeline ops bar") {
    assertResult(-1)(XdataPipelineOps.bar())
  }
}
