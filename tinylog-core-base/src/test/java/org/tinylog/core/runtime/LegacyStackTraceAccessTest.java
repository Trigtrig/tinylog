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

import java.lang.invoke.MethodHandle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyStackTraceAccessTest {

	/**
	 * Verifies that {@code sun.reflect.Reflection} is available on Java 10 and earlier.
	 */
	@EnabledForJreRange(max = JRE.JAVA_10)
	@Test
	void sunReflectionAvailable() {
		assertThat(LegacyStackTraceAccess.checkIfSunReflectionIsAvailable()).isTrue();
	}

	/**
	 * Verifies that {@code sun.reflect.Reflection} is not available on Java 11 and later.
	 */
	@EnabledForJreRange(min = JRE.JAVA_11)
	@Test
	void sunReflectionUnavailableSinceJava11() {
		assertThat(LegacyStackTraceAccess.checkIfSunReflectionIsAvailable()).isFalse();
	}

	/**
	 * Verifies that {@code sun.reflect.Reflection} is not available on Android.
	 */
	@EnabledIfSystemProperty(named = "java.runtime.name", matches = "Android Runtime")
	@Test
	void sunReflectionUnavailableOnAndroid() {
		assertThat(LegacyStackTraceAccess.checkIfSunReflectionIsAvailable()).isFalse();
	}

	/**
	 * Verifies that {@code Throwable.getStackTraceElement(int)} is available on Java 8 and earlier.
	 */
	@EnabledForJreRange(max = JRE.JAVA_8)
	@Test
	void stackTraceElementGetterAvailable() throws Throwable {
		MethodHandle handle = LegacyStackTraceAccess.getStackTraceElementGetter();
		assertThat(handle).isNotNull();

		Object result = handle.invoke(new Throwable(), 0);
		assertThat(result).isEqualTo(new StackTraceElement(
			LegacyStackTraceAccessTest.class.getCanonicalName(),
			"stackTraceElementGetterAvailable",
			LegacyStackTraceAccessTest.class.getSimpleName() + ".java",
			63
		));
	}

	/**
	 * Verifies that {@code Throwable.getStackTraceElement(int)} is not available on Java 9 and later.
	 */
	@EnabledForJreRange(min = JRE.JAVA_9)
	@Test
	void stackTraceElementGetterUnavailable() {
		assertThat(LegacyStackTraceAccess.getStackTraceElementGetter()).isNull();
	}

}
