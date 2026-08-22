package com.procurement.engine.security;

import com.procurement.engine.config.JwtAuthenticationFilter;
import com.procurement.engine.security.service.CustomUserDetailsService;
import com.procurement.engine.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86400000L);

        userDetailsService = new CustomUserDetailsService(null) {
            @Override
            public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
                if ("manager@procurement.com".equals(email)) {
                    return new User(email, "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROCUREMENT_MANAGER")));
                }
                throw new UsernameNotFoundException("User not found: " + email);
            }
        };

        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("Filter passes through without authentication when Authorization header is absent")
    void testDoFilter_NoAuthHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] filterChainCalled = new boolean[1];
        FilterChain filterChain = (req, res) -> filterChainCalled[0] = true;

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(filterChainCalled[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Filter passes through without authentication when Authorization header does not start with Bearer")
    void testDoFilter_NonBearerAuthHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] filterChainCalled = new boolean[1];
        FilterChain filterChain = (req, res) -> filterChainCalled[0] = true;

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(filterChainCalled[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Filter authenticates valid Bearer token and places Authentication into SecurityContext")
    void testDoFilter_ValidBearerToken() throws ServletException, IOException {
        String email = "manager@procurement.com";
        UserDetails userDetails = new User(email, "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROCUREMENT_MANAGER")));
        String token = jwtService.generateToken(userDetails);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] filterChainCalled = new boolean[1];
        FilterChain filterChain = (req, res) -> filterChainCalled[0] = true;

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(filterChainCalled[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(email);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_PROCUREMENT_MANAGER");
    }

    @Test
    @DisplayName("Filter clears context and continues chain when token is invalid or expired")
    void testDoFilter_InvalidOrExpiredToken() throws ServletException, IOException {
        String token = "invalid.jwt.token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] filterChainCalled = new boolean[1];
        FilterChain filterChain = (req, res) -> filterChainCalled[0] = true;

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(filterChainCalled[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
