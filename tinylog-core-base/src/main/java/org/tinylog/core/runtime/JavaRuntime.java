/*
 * Copyright 2020 Martin Winandy
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.tinylog.core.runtime;

/**
 * Runtime implementation for standard Java.
 */
public final class JavaRuntime implements RuntimeFlavor {

	/** */
	public JavaRuntime() {
	}

	@Override
	public JavaIndexBasedStackTraceLocation getStackTraceLocationAtIndex(int index) {
		return new JavaIndexBasedStackTraceLocation(index + 1);
	}

	@Override
	public JavaClassNameBasedStackTraceLocation getStackTraceLocationAfterClass(String className) {
		return new JavaClassNameBasedStackTraceLocation(className, 1);
	}

}
