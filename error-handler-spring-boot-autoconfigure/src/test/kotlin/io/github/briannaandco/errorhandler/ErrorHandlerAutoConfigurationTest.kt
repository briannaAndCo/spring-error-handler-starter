/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.briannaandco.errorhandler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class ErrorHandlerAutoConfigurationTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ErrorHandlerAutoConfiguration::class.java))

    @Test
    fun `auto configuration loads in web context`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ErrorHandlerAutoConfiguration::class.java)
        }
    }

    @Test
    fun `auto configuration does not load outside web context`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ErrorHandlerAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).doesNotHaveBean(ErrorHandlerAutoConfiguration::class.java)
            }
    }
}
