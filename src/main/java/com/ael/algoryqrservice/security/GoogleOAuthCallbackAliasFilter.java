package com.ael.algoryqrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GoogleOAuthCallbackAliasFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GoogleOAuthPaths.LEGACY_CALLBACK.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(new RewrittenUriRequest(request, GoogleOAuthPaths.CALLBACK), response);
    }

    private static final class RewrittenUriRequest extends HttpServletRequestWrapper {

        private final String rewrittenUri;

        private RewrittenUriRequest(HttpServletRequest request, String rewrittenUri) {
            super(request);
            this.rewrittenUri = rewrittenUri;
        }

        @Override
        public String getRequestURI() {
            return rewrittenUri;
        }

        @Override
        public String getServletPath() {
            return rewrittenUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            String scheme = getScheme();
            url.append(scheme).append("://").append(getServerName());
            int port = getServerPort();
            if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
                url.append(':').append(port);
            }
            url.append(rewrittenUri);
            return url;
        }
    }
}
