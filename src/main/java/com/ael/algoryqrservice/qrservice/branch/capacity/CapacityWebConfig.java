package com.ael.algoryqrservice.qrservice.branch.capacity;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CapacityWebConfig implements WebMvcConfigurer {

    private final BranchLockInterceptor branchLockInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(branchLockInterceptor).addPathPatterns("/api/v1/**");
    }
}
