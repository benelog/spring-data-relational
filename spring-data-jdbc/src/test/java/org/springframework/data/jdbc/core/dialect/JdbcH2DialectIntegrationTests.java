/*
 * Copyright 2026-present the original author or authors.
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
package org.springframework.data.jdbc.core.dialect;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.jdbc.testing.DatabaseType;
import org.springframework.data.jdbc.testing.EnabledOnDatabase;
import org.springframework.data.jdbc.testing.IntegrationTest;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Integration tests for the {@link JdbcH2Dialect}, in particular the {@link Duration} to {@code INTERVAL} conversion
 * inherited from the core H2 dialect. Start this test with {@code -Dspring.profiles.active=h2}.
 *
 * @author Jens Schauder
 */
@IntegrationTest
@EnabledOnDatabase(DatabaseType.H2)
class JdbcH2DialectIntegrationTests {

	@Autowired JdbcAggregateOperations template;

	@Test // GH-2299
	void shouldReadAndWriteDurationAsInterval() {

		DurationHolder holder = new DurationHolder();
		holder.duration = Duration.ofSeconds(42, 123_000_000);

		DurationHolder saved = template.insert(holder);

		DurationHolder loaded = template.findById(saved.id, DurationHolder.class);

		assertThat(loaded).isNotNull();
		assertThat(loaded.duration).isEqualTo(holder.duration);
	}

	@Configuration
	@Import(TestConfiguration.class)
	static class Config {}

	static class DurationHolder {

		@Id Long id;
		Duration duration;
	}
}
