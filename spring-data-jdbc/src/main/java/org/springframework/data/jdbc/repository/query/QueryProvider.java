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
package org.springframework.data.jdbc.repository.query;

import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.util.ObjectUtils;

/**
 * SPI to generate the SQL statement for a repository query method at runtime, based on the actual parameter values.
 * <p>
 * Implementations are configured via {@link Query#queryProviderClass()} and get instantiated using their default
 * constructor. {@link #getQuery(SqlParameterSource)} gets invoked on every query method execution with the fully bound
 * parameter source, so that implementations can inspect the bound values and compose the SQL statement accordingly,
 * e.g. by appending conditions only for non-{@literal null} parameters. Parameter binding, query execution, and result
 * mapping remain the responsibility of Spring Data JDBC.
 * <p>
 * The returned SQL statement may contain named parameters matching the bound parameter names. Value expressions
 * ({@code :#{…}}) and table name expressions ({@code #{#tableName}}) are not supported within the returned SQL
 * statement. Implementations must be thread-safe as a single instance is shared across query method executions.
 *
 * @author Sanghyuk Jung
 * @since 4.2
 * @see Query#queryProviderClass()
 */
@FunctionalInterface
public interface QueryProvider {

	/**
	 * Returns the SQL statement to execute for the actual query method invocation.
	 *
	 * @param parameterSource the fully bound method parameters. Must only be inspected, not mutated.
	 * @return the SQL statement to execute. Must not be {@literal null} or empty.
	 */
	String getQuery(SqlParameterSource parameterSource);

	/**
	 * Returns whether the given parameter is bound and its value is not {@literal null}.
	 *
	 * @param parameterSource the parameter source to inspect.
	 * @param parameterName the name of the parameter.
	 * @return {@literal true} if the parameter is bound and its value is not {@literal null}.
	 */
	default boolean isNotNull(SqlParameterSource parameterSource, String parameterName) {
		return parameterSource.hasValue(parameterName) && parameterSource.getValue(parameterName) != null;
	}

	/**
	 * Returns whether the given parameter is bound and its value is neither {@literal null} nor empty.
	 *
	 * @param parameterSource the parameter source to inspect.
	 * @param parameterName the name of the parameter.
	 * @return {@literal true} if the parameter is bound and its value is neither {@literal null} nor empty.
	 * @see ObjectUtils#isEmpty(Object)
	 */
	default boolean isNotEmpty(SqlParameterSource parameterSource, String parameterName) {
		return parameterSource.hasValue(parameterName) && !ObjectUtils.isEmpty(parameterSource.getValue(parameterName));
	}
}
