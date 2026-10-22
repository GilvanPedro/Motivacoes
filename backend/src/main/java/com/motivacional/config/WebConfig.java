package com.motivacional.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final AdminInterceptor adminInterceptor;

    public WebConfig(AppProperties props, AdminInterceptor adminInterceptor) {
        this.props = props;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (props.corsOrigins() == null || props.corsOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(props.corsOrigins().stream().map(String::trim).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor).addPathPatterns("/api/**");
    }
}
