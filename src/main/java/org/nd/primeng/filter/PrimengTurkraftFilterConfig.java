package org.nd.primeng.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class PrimengTurkraftFilterConfig {

	@Bean
	public FilterRegistrationBean<PrimengTurkraftFilter> loggingFilterRegistration() {
		FilterRegistrationBean<PrimengTurkraftFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new PrimengTurkraftFilter());
		registration.setOrder(Ordered.LOWEST_PRECEDENCE);
		return registration;
	}

}
