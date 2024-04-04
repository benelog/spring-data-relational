/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.security.config.annotation.method.configuration;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.JdkRegexpMethodPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationConfigurationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.annotation.BusinessService;
import org.springframework.security.access.annotation.BusinessServiceImpl;
import org.springframework.security.access.annotation.ExpressionProtectedBusinessServiceImpl;
import org.springframework.security.access.annotation.Jsr250BusinessServiceImpl;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory.TargetVisitor;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.AuthorizeReturnObject;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedPostProcessor;
import org.springframework.security.authorization.method.MethodInvocationResult;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.SecurityContextChangedListenerConfig;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.test.SpringTestContext;
import org.springframework.security.config.test.SpringTestContextExtension;
import org.springframework.security.config.test.SpringTestParentApplicationContextExecutionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link PrePostMethodSecurityConfiguration}.
 *
 * @author Evgeniy Cheban
 * @author Josh Cummings
 */
@ExtendWith({ SpringExtension.class, SpringTestContextExtension.class })
@ContextConfiguration(classes = SecurityContextChangedListenerConfig.class)
@TestExecutionListeners(listeners = { WithSecurityContextTestExecutionListener.class,
		SpringTestParentApplicationContextExecutionListener.class })
public class PrePostMethodSecurityConfigurationTests {

	public final SpringTestContext spring = new SpringTestContext(this);

	@Autowired(required = false)
	MethodSecurityService methodSecurityService;

	@Autowired(required = false)
	BusinessService businessService;

	@WithMockUser
	@Test
	public void customMethodSecurityPreAuthorizeAdminWhenRoleUserThenAccessDeniedException() {
		this.spring.register(CustomMethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorizeAdmin)
			.withMessage("Access Denied");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void customMethodSecurityPreAuthorizeAdminWhenRoleAdminThenPasses() {
		this.spring.register(CustomMethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void preAuthorizeWhenRoleAdminThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorize)
			.withMessage("Access Denied");
	}

	@WithAnonymousUser
	@Test
	public void preAuthorizePermitAllWhenRoleAnonymousThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.preAuthorizePermitAll();
		assertThat(result).isNull();
	}

	@WithAnonymousUser
	@Test
	public void preAuthorizeNotAnonymousWhenRoleAnonymousThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(this.methodSecurityService::preAuthorizeNotAnonymous)
			.withMessage("Access Denied");
	}

	@WithMockUser
	@Test
	public void preAuthorizeNotAnonymousWhenRoleUserThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeNotAnonymous();
	}

	@WithMockUser
	@Test
	public void securedWhenRoleUserThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::secured)
			.withMessage("Access Denied");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void securedWhenRoleAdminThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.secured();
		assertThat(result).isNull();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void securedUserWhenRoleAdminThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::securedUser)
			.withMessage("Access Denied");
		SecurityContextHolderStrategy strategy = this.spring.getContext().getBean(SecurityContextHolderStrategy.class);
		verify(strategy, atLeastOnce()).getContext();
	}

	@WithMockUser
	@Test
	public void securedUserWhenRoleUserThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.securedUser();
		assertThat(result).isNull();
	}

	@WithMockUser
	@Test
	public void preAuthorizeAdminWhenRoleUserThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::preAuthorizeAdmin)
			.withMessage("Access Denied");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void preAuthorizeAdminWhenRoleAdminThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void preAuthorizeAdminWhenSecurityContextHolderStrategyThenUses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
		SecurityContextHolderStrategy strategy = this.spring.getContext().getBean(SecurityContextHolderStrategy.class);
		verify(strategy, atLeastOnce()).getContext();
	}

	@WithMockUser(authorities = "PREFIX_ADMIN")
	@Test
	public void preAuthorizeAdminWhenRoleAdminAndCustomPrefixThenPasses() {
		this.spring.register(CustomGrantedAuthorityDefaultsConfig.class, MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeAdmin();
	}

	@WithMockUser
	@Test
	public void postHasPermissionWhenParameterIsNotGrantThenAccessDeniedException() {
		this.spring.register(CustomPermissionEvaluatorConfig.class, MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.postHasPermission("deny"))
			.withMessage("Access Denied");
	}

	@WithMockUser
	@Test
	public void postHasPermissionWhenParameterIsGrantThenPasses() {
		this.spring.register(CustomPermissionEvaluatorConfig.class, MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.postHasPermission("grant");
		assertThat(result).isNull();
	}

	@WithMockUser
	@Test
	public void postAnnotationWhenParameterIsNotGrantThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.postAnnotation("deny"))
			.withMessage("Access Denied");
	}

	@WithMockUser
	@Test
	public void postAnnotationWhenParameterIsGrantThenPasses() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		String result = this.methodSecurityService.postAnnotation("grant");
		assertThat(result).isNull();
	}

	@WithMockUser("bob")
	@Test
	public void methodReturningAListWhenPrePostFiltersConfiguredThenFiltersList() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		List<String> names = new ArrayList<>();
		names.add("bob");
		names.add("joe");
		names.add("sam");
		List<?> result = this.businessService.methodReturningAList(names);
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo("bob");
	}

	@WithMockUser("bob")
	@Test
	public void methodReturningAnArrayWhenPostFilterConfiguredThenFiltersArray() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		List<String> names = new ArrayList<>();
		names.add("bob");
		names.add("joe");
		names.add("sam");
		Object[] result = this.businessService.methodReturningAnArray(names.toArray());
		assertThat(result).hasSize(1);
		assertThat(result[0]).isEqualTo("bob");
	}

	@WithMockUser("bob")
	@Test
	public void securedUserWhenCustomBeforeAdviceConfiguredAndNameBobThenPasses() {
		this.spring.register(CustomAuthorizationManagerBeforeAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		String result = this.methodSecurityService.securedUser();
		assertThat(result).isNull();
	}

	@WithMockUser("joe")
	@Test
	public void securedUserWhenCustomBeforeAdviceConfiguredAndNameNotBobThenAccessDeniedException() {
		this.spring.register(CustomAuthorizationManagerBeforeAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::securedUser)
			.withMessage("Access Denied");
	}

	@WithMockUser("bob")
	@Test
	public void securedUserWhenCustomAfterAdviceConfiguredAndNameBobThenGranted() {
		this.spring.register(CustomAuthorizationManagerAfterAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		String result = this.methodSecurityService.securedUser();
		assertThat(result).isEqualTo("granted");
	}

	@WithMockUser("joe")
	@Test
	public void securedUserWhenCustomAfterAdviceConfiguredAndNameNotBobThenAccessDeniedException() {
		this.spring.register(CustomAuthorizationManagerAfterAdviceConfig.class, MethodSecurityServiceConfig.class)
			.autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::securedUser)
			.withMessage("Access Denied for User 'joe'");
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void jsr250WhenRoleAdminThenAccessDeniedException() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.methodSecurityService::jsr250)
			.withMessage("Access Denied");
	}

	@WithAnonymousUser
	@Test
	public void jsr250PermitAllWhenRoleAnonymousThenPasses() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		String result = this.methodSecurityService.jsr250PermitAll();
		assertThat(result).isNull();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void rolesAllowedUserWhenRoleAdminThenAccessDeniedException() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(this.businessService::rolesAllowedUser)
			.withMessage("Access Denied");
		SecurityContextHolderStrategy strategy = this.spring.getContext().getBean(SecurityContextHolderStrategy.class);
		verify(strategy, atLeastOnce()).getContext();
	}

	@WithMockUser
	@Test
	public void rolesAllowedUserWhenRoleUserThenPasses() {
		this.spring.register(BusinessServiceConfig.class).autowire();
		this.businessService.rolesAllowedUser();
	}

	@WithMockUser(roles = { "ADMIN", "USER" })
	@Test
	public void manyAnnotationsWhenMeetsConditionsThenReturnsFilteredList() throws Exception {
		List<String> names = Arrays.asList("harold", "jonathan", "pete", "bo");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		List<String> filtered = this.methodSecurityService.manyAnnotations(new ArrayList<>(names));
		assertThat(filtered).hasSize(2);
		assertThat(filtered).containsExactly("harold", "jonathan");
	}

	// gh-4003
	// gh-4103
	@WithMockUser
	@Test
	public void manyAnnotationsWhenUserThenFails() {
		List<String> names = Arrays.asList("harold", "jonathan", "pete", "bo");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.manyAnnotations(new ArrayList<>(names)));
	}

	@WithMockUser
	@Test
	public void manyAnnotationsWhenShortListThenFails() {
		List<String> names = Arrays.asList("harold", "jonathan", "pete");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.manyAnnotations(new ArrayList<>(names)));
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void manyAnnotationsWhenAdminThenFails() {
		List<String> names = Arrays.asList("harold", "jonathan", "pete", "bo");
		this.spring.register(MethodSecurityServiceEnabledConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.manyAnnotations(new ArrayList<>(names)));
	}

	// gh-3183
	@Test
	public void repeatedAnnotationsWhenPresentThenFails() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		assertThatExceptionOfType(AnnotationConfigurationException.class)
			.isThrownBy(() -> this.methodSecurityService.repeatedAnnotations());
	}

	// gh-3183
	@Test
	public void repeatedJsr250AnnotationsWhenPresentThenFails() {
		this.spring.register(Jsr250Config.class).autowire();
		assertThatExceptionOfType(AnnotationConfigurationException.class)
			.isThrownBy(() -> this.businessService.repeatedAnnotations());
	}

	// gh-3183
	@Test
	public void repeatedSecuredAnnotationsWhenPresentThenFails() {
		this.spring.register(SecuredConfig.class).autowire();
		assertThatExceptionOfType(AnnotationConfigurationException.class)
			.isThrownBy(() -> this.businessService.repeatedAnnotations());
	}

	@WithMockUser
	@Test
	public void preAuthorizeWhenAuthorizationEventPublisherThenUses() {
		this.spring.register(MethodSecurityServiceConfig.class, AuthorizationEventPublisherConfig.class).autowire();
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.preAuthorize());
		AuthorizationEventPublisher publisher = this.spring.getContext().getBean(AuthorizationEventPublisher.class);
		verify(publisher).publishAuthorizationEvent(any(Supplier.class), any(MethodInvocation.class),
				any(AuthorizationDecision.class));
	}

	@WithMockUser
	@Test
	public void postAuthorizeWhenAuthorizationEventPublisherThenUses() {
		this.spring.register(MethodSecurityServiceConfig.class, AuthorizationEventPublisherConfig.class).autowire();
		this.methodSecurityService.postAnnotation("grant");
		AuthorizationEventPublisher publisher = this.spring.getContext().getBean(AuthorizationEventPublisher.class);
		verify(publisher).publishAuthorizationEvent(any(Supplier.class), any(MethodInvocationResult.class),
				any(AuthorizationDecision.class));
	}

	// gh-10305
	@WithMockUser
	@Test
	public void beanInSpelWhenEvaluatedThenLooksUpBean() {
		this.spring.register(MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeBean(true);
	}

	@Test
	public void configureWhenAspectJThenRegistersAspects() {
		this.spring.register(AspectJMethodSecurityServiceConfig.class).autowire();
		assertThat(this.spring.getContext().containsBean("preFilterAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("postFilterAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("preAuthorizeAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("postAuthorizeAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("securedAspect$0")).isTrue();
		assertThat(this.spring.getContext().containsBean("annotationSecurityAspect$0")).isFalse();
	}

	@Test
	public void configureWhenBeanOverridingDisallowedThenWorks() {
		this.spring.register(MethodSecurityServiceConfig.class, BusinessServiceConfig.class)
			.postProcessor(disallowBeanOverriding())
			.autowire();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void methodSecurityAdminWhenRoleHierarchyBeanAvailableThenUses() {
		this.spring.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@WithMockUser
	@Test
	public void methodSecurityUserWhenRoleHierarchyBeanAvailableThenUses() {
		this.spring.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class).autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@WithMockUser(roles = "ADMIN")
	@Test
	public void methodSecurityAdminWhenAuthorizationEventPublisherBeanAvailableThenUses() {
		this.spring
			.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class,
					AuthorizationEventPublisherConfig.class)
			.autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@WithMockUser
	@Test
	public void methodSecurityUserWhenAuthorizationEventPublisherBeanAvailableThenUses() {
		this.spring
			.register(RoleHierarchyConfig.class, MethodSecurityServiceConfig.class,
					AuthorizationEventPublisherConfig.class)
			.autowire();
		this.methodSecurityService.preAuthorizeUser();
		this.methodSecurityService.securedUser();
		this.methodSecurityService.jsr250RolesAllowedUser();
	}

	@Test
	public void allAnnotationsWhenAdviceBeforeOffsetPreFilterThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPreFilterConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(5);
		assertThat(filtered).containsExactly("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
	}

	@Test
	public void allAnnotationsWhenAdviceBeforeOffsetPreAuthorizeThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPreAuthorizeConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(4);
		assertThat(filtered).containsExactly("DropOnPreAuthorize", "DropOnPostAuthorize", "DropOnPostFilter",
				"DoNotDrop");
	}

	@Test
	public void allAnnotationsWhenAdviceBeforeOffsetSecuredThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetSecuredConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(3);
		assertThat(filtered).containsExactly("DropOnPostAuthorize", "DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser
	public void allAnnotationsWhenAdviceBeforeOffsetJsr250WithInsufficientRolesThenFails() {
		this.spring.register(ReturnBeforeOffsetJsr250Config.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.allAnnotations(new ArrayList<>(list)));
	}

	@Test
	@WithMockUser(roles = "SECURED")
	public void allAnnotationsWhenAdviceBeforeOffsetJsr250ThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetJsr250Config.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(3);
		assertThat(filtered).containsExactly("DropOnPostAuthorize", "DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser(roles = { "SECURED" })
	public void allAnnotationsWhenAdviceBeforeOffsetPostAuthorizeWithInsufficientRolesThenFails() {
		this.spring.register(ReturnBeforeOffsetPostAuthorizeConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.methodSecurityService.allAnnotations(new ArrayList<>(list)));
	}

	@Test
	@WithMockUser(roles = { "SECURED", "JSR250" })
	public void allAnnotationsWhenAdviceBeforeOffsetPostAuthorizeThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPostAuthorizeConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(3);
		assertThat(filtered).containsExactly("DropOnPostAuthorize", "DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser(roles = { "SECURED", "JSR250" })
	public void allAnnotationsWhenAdviceBeforeOffsetPostFilterThenReturnsFilteredList() {
		this.spring.register(ReturnBeforeOffsetPostFilterConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(2);
		assertThat(filtered).containsExactly("DropOnPostFilter", "DoNotDrop");
	}

	@Test
	@WithMockUser(roles = { "SECURED", "JSR250" })
	public void allAnnotationsWhenAdviceAfterAllOffsetThenReturnsFilteredList() {
		this.spring.register(ReturnAfterAllOffsetConfig.class).autowire();
		List<String> list = Arrays.asList("DropOnPreFilter", "DropOnPreAuthorize", "DropOnPostAuthorize",
				"DropOnPostFilter", "DoNotDrop");
		List<String> filtered = this.methodSecurityService.allAnnotations(new ArrayList<>(list));
		assertThat(filtered).hasSize(1);
		assertThat(filtered).containsExactly("DoNotDrop");
	}

	@Test
	@WithMockUser
	public void methodeWhenParameterizedPreAuthorizeMetaAnnotationThenPasses() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.hasRole("USER")).isTrue();
	}

	@Test
	@WithMockUser
	public void methodRoleWhenPreAuthorizeMetaAnnotationHardcodedParameterThenPasses() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.hasUserRole()).isTrue();
	}

	@Test
	public void methodWhenParameterizedAnnotationThenFails() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(service::placeholdersOnlyResolvedByMetaAnnotations);
	}

	@Test
	@WithMockUser(authorities = "SCOPE_message:read")
	public void methodWhenMultiplePlaceholdersHasAuthorityThenPasses() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.readMessage()).isEqualTo("message");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	public void methodWhenMultiplePlaceholdersHasRoleThenPasses() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.readMessage()).isEqualTo("message");
	}

	@Test
	@WithMockUser
	public void methodWhenPostAuthorizeMetaAnnotationThenAuthorizes() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		service.startsWithDave("daveMatthews");
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> service.startsWithDave("jenniferHarper"));
	}

	@Test
	@WithMockUser
	public void methodWhenPreFilterMetaAnnotationThenFilters() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.parametersContainDave(new ArrayList<>(List.of("dave", "carla", "vanessa", "paul"))))
			.containsExactly("dave");
	}

	@Test
	@WithMockUser
	public void methodWhenPostFilterMetaAnnotationThenFilters() {
		this.spring.register(MetaAnnotationPlaceholderConfig.class).autowire();
		MetaAnnotationService service = this.spring.getContext().getBean(MetaAnnotationService.class);
		assertThat(service.resultsContainDave(new ArrayList<>(List.of("dave", "carla", "vanessa", "paul"))))
			.containsExactly("dave");
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findByIdWhenAuthorizedResultThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = flights.findById("1");
		assertThatNoException().isThrownBy(flight::getAltitude);
		assertThatNoException().isThrownBy(flight::getSeats);
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findByIdWhenUnauthorizedResultThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		Flight flight = flights.findById("1");
		assertThatNoException().isThrownBy(flight::getSeats);
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findAllWhenUnauthorizedResultThenDenies() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll().forEachRemaining((flight) -> {
			assertThatNoException().isThrownBy(flight::getSeats);
			assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(flight::getAltitude);
		});
	}

	@Test
	public void removeWhenAuthorizedResultThenRemoves() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.remove("1");
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findAllWhenPostFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll()
			.forEachRemaining((flight) -> assertThat(flight.getPassengers()).extracting(Passenger::getName)
				.doesNotContain("Kevin Mitnick"));
	}

	@Test
	@WithMockUser(authorities = "airplane:read")
	public void findAllWhenPreFilterThenFilters() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll().forEachRemaining((flight) -> {
			flight.board(new ArrayList<>(List.of("John")));
			assertThat(flight.getPassengers()).extracting(Passenger::getName).doesNotContain("John");
			flight.board(new ArrayList<>(List.of("John Doe")));
			assertThat(flight.getPassengers()).extracting(Passenger::getName).contains("John Doe");
		});
	}

	@Test
	@WithMockUser(authorities = "seating:read")
	public void findAllWhenNestedPreAuthorizeThenAuthorizes() {
		this.spring.register(AuthorizeResultConfig.class).autowire();
		FlightRepository flights = this.spring.getContext().getBean(FlightRepository.class);
		flights.findAll().forEachRemaining((flight) -> {
			List<Passenger> passengers = flight.getPassengers();
			passengers.forEach((passenger) -> assertThatExceptionOfType(AccessDeniedException.class)
				.isThrownBy(passenger::getName));
		});
	}

	@Test
	@WithMockUser
	void getCardNumberWhenPostAuthorizeAndNotAdminThenReturnMasked() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class,
					MethodSecurityService.CardNumberMaskingPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String cardNumber = service.postAuthorizeGetCardNumberIfAdmin("4444-3333-2222-1111");
		assertThat(cardNumber).isEqualTo("****-****-****-1111");
	}

	@Test
	@WithMockUser
	void getCardNumberWhenPreAuthorizeAndNotAdminThenReturnMasked() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.StarMaskingHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String cardNumber = service.preAuthorizeGetCardNumberIfAdmin("4444-3333-2222-1111");
		assertThat(cardNumber).isEqualTo("***");
	}

	@Test
	@WithMockUser
	void getCardNumberWhenPreAuthorizeAndNotAdminAndChildHandlerThenResolveCorrectHandlerAndReturnMasked() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.StarMaskingHandler.class,
					MethodSecurityService.StartMaskingHandlerChild.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String cardNumber = service.preAuthorizeWithHandlerChildGetCardNumberIfAdmin("4444-3333-2222-1111");
		assertThat(cardNumber).isEqualTo("***-child");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void preAuthorizeWhenHandlerAndAccessDeniedNotThrownFromPreAuthorizeThenNotHandled() {
		this.spring.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.StarMaskingHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(service::preAuthorizeThrowAccessDeniedManually);
	}

	@Test
	@WithMockUser
	void preAuthorizeWhenDeniedAndHandlerWithCustomAnnotationThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeDeniedMethodWithMaskAnnotation();
		assertThat(result).isEqualTo("methodmask");
	}

	@Test
	@WithMockUser
	void preAuthorizeWhenDeniedAndHandlerWithCustomAnnotationInClassThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeDeniedMethodWithNoMaskAnnotation();
		assertThat(result).isEqualTo("classmask");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void postAuthorizeWhenHandlerAndAccessDeniedNotThrownFromPostAuthorizeThenNotHandled() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.PostMaskingPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(service::postAuthorizeThrowAccessDeniedManually);
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenNullDeniedMetaAnnotationThanWorks() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MetaAnnotationPlaceholderConfig.class,
					MethodSecurityService.NullPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeDeniedWithNullDenied();
		assertThat(result).isNull();
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenDeniedAndHandlerWithCustomAnnotationThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeDeniedMethodWithMaskAnnotation();
		assertThat(result).isEqualTo("methodmask");
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenDeniedAndHandlerWithCustomAnnotationInClassThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeDeniedMethodWithNoMaskAnnotation();
		assertThat(result).isEqualTo("classmask");
	}

	@Test
	@WithMockUser
	void postAuthorizeWhenDeniedAndHandlerWithCustomAnnotationUsingBeanThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("ok-masked");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void postAuthorizeWhenAllowedAndHandlerWithCustomAnnotationUsingBeanThenInvokeMethodNormally() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationPostProcessor.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.postAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("ok");
	}

	@Test
	@WithMockUser
	void preAuthorizeWhenDeniedAndHandlerWithCustomAnnotationUsingBeanThenHandlerCanUseMaskFromOtherAnnotation() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("mask");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void preAuthorizeWhenAllowedAndHandlerWithCustomAnnotationUsingBeanThenInvokeMethodNormally() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.MaskAnnotationHandler.class,
					MyMasker.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		String result = service.preAuthorizeWithMaskAnnotationUsingBean();
		assertThat(result).isEqualTo("ok");
	}

	@Test
	@WithMockUser
	void getUserWhenAuthorizedAndUserEmailIsProtectedAndNotAuthorizedThenReturnEmailMasked() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class,
					UserRecordWithEmailProtected.EmailMaskingPostProcessor.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		UserRecordWithEmailProtected user = service.getUserRecordWithEmailProtected();
		assertThat(user.email()).isEqualTo("use******@example.com");
		assertThat(user.name()).isEqualTo("username");
	}

	@Test
	@WithMockUser
	void getUserWhenNotAuthorizedAndHandlerFallbackValueThenReturnFallbackValue() {
		this.spring
			.register(MethodSecurityServiceEnabledConfig.class, MethodSecurityService.UserFallbackDeniedHandler.class)
			.autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		UserRecordWithEmailProtected user = service.getUserWithFallbackWhenUnauthorized();
		assertThat(user.email()).isEqualTo("Protected");
		assertThat(user.name()).isEqualTo("Protected");
	}

	@Test
	@WithMockUser
	void getUserWhenNotAuthorizedThenHandlerUsesCustomAuthorizationDecision() {
		this.spring.register(MethodSecurityServiceConfig.class, CustomResultConfig.class).autowire();
		MethodSecurityService service = this.spring.getContext().getBean(MethodSecurityService.class);
		MethodAuthorizationDeniedHandler handler = this.spring.getContext()
			.getBean(MethodAuthorizationDeniedHandler.class);
		MethodAuthorizationDeniedPostProcessor postProcessor = this.spring.getContext()
			.getBean(MethodAuthorizationDeniedPostProcessor.class);
		assertThat(service.checkCustomResult(false)).isNull();
		verify(handler).handle(any(), any(Authz.AuthzResult.class));
		verifyNoInteractions(postProcessor);
		assertThat(service.checkCustomResult(true)).isNull();
		verify(postProcessor).postProcessResult(any(), any(Authz.AuthzResult.class));
		verifyNoMoreInteractions(handler);
	}

	private static Consumer<ConfigurableWebApplicationContext> disallowBeanOverriding() {
		return (context) -> ((AnnotationConfigWebApplicationContext) context).setAllowBeanDefinitionOverriding(false);
	}

	private static Advisor returnAdvisor(int order) {
		JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
		pointcut.setPattern(".*MethodSecurityServiceImpl.*");
		MethodInterceptor interceptor = (mi) -> mi.getArguments()[0];
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
		advisor.setOrder(order);
		return advisor;
	}

	@Configuration
	static class AuthzConfig {

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableCustomMethodSecurity
	static class CustomMethodSecurityServiceConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class MethodSecurityServiceConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableMethodSecurity(jsr250Enabled = true)
	static class BusinessServiceConfig {

		@Bean
		BusinessService businessService() {
			return new ExpressionProtectedBusinessServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity(prePostEnabled = false, securedEnabled = true)
	static class SecuredConfig {

		@Bean
		BusinessService businessService() {
			return new BusinessServiceImpl<>();
		}

	}

	@Configuration
	@EnableMethodSecurity(prePostEnabled = false, jsr250Enabled = true)
	static class Jsr250Config {

		@Bean
		BusinessService businessService() {
			return new Jsr250BusinessServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
	static class MethodSecurityServiceEnabledConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomPermissionEvaluatorConfig {

		@Bean
		MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
			DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
			expressionHandler.setPermissionEvaluator(new PermissionEvaluator() {
				@Override
				public boolean hasPermission(Authentication authentication, Object targetDomainObject,
						Object permission) {
					return "grant".equals(targetDomainObject);
				}

				@Override
				public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
						Object permission) {
					throw new UnsupportedOperationException();
				}
			});
			return expressionHandler;
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomGrantedAuthorityDefaultsConfig {

		@Bean
		GrantedAuthorityDefaults grantedAuthorityDefaults() {
			return new GrantedAuthorityDefaults("PREFIX_");
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomAuthorizationManagerBeforeAdviceConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor customBeforeAdvice(SecurityContextHolderStrategy strategy) {
			JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
			pointcut.setPattern(".*MethodSecurityServiceImpl.*securedUser");
			AuthorizationManager<MethodInvocation> authorizationManager = (a,
					o) -> new AuthorizationDecision("bob".equals(a.get().getName()));
			AuthorizationManagerBeforeMethodInterceptor before = new AuthorizationManagerBeforeMethodInterceptor(
					pointcut, authorizationManager);
			before.setSecurityContextHolderStrategy(strategy);
			return before;
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class CustomAuthorizationManagerAfterAdviceConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor customAfterAdvice(SecurityContextHolderStrategy strategy) {
			JdkRegexpMethodPointcut pointcut = new JdkRegexpMethodPointcut();
			pointcut.setPattern(".*MethodSecurityServiceImpl.*securedUser");
			MethodInterceptor interceptor = (mi) -> {
				Authentication auth = strategy.getContext().getAuthentication();
				if ("bob".equals(auth.getName())) {
					return "granted";
				}
				throw new AccessDeniedException("Access Denied for User '" + auth.getName() + "'");
			};
			DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
			advisor.setOrder(AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + 1);
			return advisor;
		}

	}

	@Configuration
	static class AuthorizationEventPublisherConfig {

		private final AuthorizationEventPublisher publisher = mock(AuthorizationEventPublisher.class);

		@Bean
		AuthorizationEventPublisher authorizationEventPublisher() {
			return this.publisher;
		}

	}

	@EnableMethodSecurity(mode = AdviceMode.ASPECTJ, securedEnabled = true)
	static class AspectJMethodSecurityServiceConfig {

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true)
	static class RoleHierarchyConfig {

		@Bean
		static RoleHierarchy roleHierarchy() {
			RoleHierarchyImpl roleHierarchyImpl = new RoleHierarchyImpl();
			roleHierarchyImpl.setHierarchy("ROLE_ADMIN > ROLE_USER");
			return roleHierarchyImpl;
		}

	}

	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPreFilterConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePreFilter() {
			return returnAdvisor(AuthorizationInterceptorsOrder.PRE_FILTER.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPreAuthorizeConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePreAuthorize() {
			return returnAdvisor(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetSecuredConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforeSecured() {
			return returnAdvisor(AuthorizationInterceptorsOrder.SECURED.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetJsr250Config {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforeJsr250() {
			return returnAdvisor(AuthorizationInterceptorsOrder.JSR250.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPostAuthorizeConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePreAuthorize() {
			return returnAdvisor(AuthorizationInterceptorsOrder.POST_AUTHORIZE.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnBeforeOffsetPostFilterConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnBeforePostFilter() {
			return returnAdvisor(AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + OffsetConfig.OFFSET - 1);
		}

	}

	@Configuration
	@Import(OffsetConfig.class)
	static class ReturnAfterAllOffsetConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor returnAfterAll() {
			return returnAdvisor(AuthorizationInterceptorsOrder.POST_FILTER.getOrder() + OffsetConfig.OFFSET + 1);
		}

	}

	@Configuration
	@EnableMethodSecurity(offset = OffsetConfig.OFFSET, jsr250Enabled = true, securedEnabled = true)
	static class OffsetConfig {

		static final int OFFSET = 2;

		@Bean
		MethodSecurityService methodSecurityService() {
			return new MethodSecurityServiceImpl();
		}

		@Bean
		Authz authz() {
			return new Authz();
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class MetaAnnotationPlaceholderConfig {

		@Bean
		PrePostTemplateDefaults methodSecurityDefaults() {
			return new PrePostTemplateDefaults();
		}

		@Bean
		MetaAnnotationService metaAnnotationService() {
			return new MetaAnnotationService();
		}

	}

	static class MetaAnnotationService {

		@RequireRole(role = "#role")
		boolean hasRole(String role) {
			return true;
		}

		@RequireRole(role = "'USER'")
		boolean hasUserRole() {
			return true;
		}

		@PreAuthorize("hasRole({role})")
		void placeholdersOnlyResolvedByMetaAnnotations() {
		}

		@HasClaim(claim = "message:read", roles = { "'ADMIN'" })
		String readMessage() {
			return "message";
		}

		@ResultStartsWith("dave")
		String startsWithDave(String value) {
			return value;
		}

		@ParameterContains("dave")
		List<String> parametersContainDave(List<String> list) {
			return list;
		}

		@ResultContains("dave")
		List<String> resultsContainDave(List<String> list) {
			return list;
		}

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreAuthorize("hasRole({role})")
	@interface RequireRole {

		String role();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreAuthorize("hasAuthority('SCOPE_{claim}') || hasAnyRole({roles})")
	@interface HasClaim {

		String claim();

		String[] roles() default {};

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PostAuthorize("returnObject.startsWith('{value}')")
	@interface ResultStartsWith {

		String value();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PreFilter("filterObject.contains('{value}')")
	@interface ParameterContains {

		String value();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@PostFilter("filterObject.contains('{value}')")
	@interface ResultContains {

		String value();

	}

	@EnableMethodSecurity
	@Configuration
	static class AuthorizeResultConfig {

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		static Customizer<AuthorizationAdvisorProxyFactory> skipValueTypes() {
			return (f) -> f.setTargetVisitor(TargetVisitor.defaultsSkipValueTypes());
		}

		@Bean
		FlightRepository flights() {
			FlightRepository flights = new FlightRepository();
			Flight one = new Flight("1", 35000d, 35);
			one.board(new ArrayList<>(List.of("Marie Curie", "Kevin Mitnick", "Ada Lovelace")));
			flights.save(one);
			Flight two = new Flight("2", 32000d, 72);
			two.board(new ArrayList<>(List.of("Albert Einstein")));
			flights.save(two);
			return flights;
		}

		@Bean
		RoleHierarchy roleHierarchy() {
			return RoleHierarchyImpl.withRolePrefix("").role("airplane:read").implies("seating:read").build();
		}

	}

	@AuthorizeReturnObject
	static class FlightRepository {

		private final Map<String, Flight> flights = new ConcurrentHashMap<>();

		Iterator<Flight> findAll() {
			return this.flights.values().iterator();
		}

		Flight findById(String id) {
			return this.flights.get(id);
		}

		Flight save(Flight flight) {
			this.flights.put(flight.getId(), flight);
			return flight;
		}

		void remove(String id) {
			this.flights.remove(id);
		}

	}

	@AuthorizeReturnObject
	static class Flight {

		private final String id;

		private final Double altitude;

		private final Integer seats;

		private final List<Passenger> passengers = new ArrayList<>();

		Flight(String id, Double altitude, Integer seats) {
			this.id = id;
			this.altitude = altitude;
			this.seats = seats;
		}

		String getId() {
			return this.id;
		}

		@PreAuthorize("hasAuthority('airplane:read')")
		Double getAltitude() {
			return this.altitude;
		}

		@PreAuthorize("hasAuthority('seating:read')")
		Integer getSeats() {
			return this.seats;
		}

		@PostAuthorize("hasAuthority('seating:read')")
		@PostFilter("filterObject.name != 'Kevin Mitnick'")
		List<Passenger> getPassengers() {
			return this.passengers;
		}

		@PreAuthorize("hasAuthority('seating:read')")
		@PreFilter("filterObject.contains(' ')")
		void board(List<String> passengers) {
			for (String passenger : passengers) {
				this.passengers.add(new Passenger(passenger));
			}
		}

	}

	public static class Passenger {

		String name;

		public Passenger(String name) {
			this.name = name;
		}

		@PreAuthorize("hasAuthority('airplane:read')")
		public String getName() {
			return this.name;
		}

	}

	@EnableMethodSecurity
	static class CustomResultConfig {

		MethodAuthorizationDeniedHandler handler = mock(MethodAuthorizationDeniedHandler.class);

		MethodAuthorizationDeniedPostProcessor postProcessor = mock(MethodAuthorizationDeniedPostProcessor.class);

		@Bean
		MethodAuthorizationDeniedHandler methodAuthorizationDeniedHandler() {
			return this.handler;
		}

		@Bean
		MethodAuthorizationDeniedPostProcessor methodAuthorizationDeniedPostProcessor() {
			return this.postProcessor;
		}

	}

}
