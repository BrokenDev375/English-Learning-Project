package vn.edu.ptit.english.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import vn.edu.ptit.english.repository.UserRepository;
import vn.edu.ptit.english.security.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(AuthValidation.normalizeEmail(email)).map(UserPrincipal::from)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(tokens)));
    }
    @Bean ServletListenerRegistrationBean<LiveConnections> sessionListener(LiveConnections connections) {
        return new ServletListenerRegistrationBean<>(connections);
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json, CsrfTokenRepository tokens,
                                                  SecurityContextRepository contexts, AccountGuard guard) throws Exception {
        return http
            .csrf(csrf -> csrf.csrfTokenRepository(tokens).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
            .requestCache(cache -> cache.disable())
            .addFilterAfter(new AccountGuardFilter(guard, json), SecurityContextHolderFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/login", "/api/auth/register", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/author/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/assets").hasAnyRole("TEACHER","ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) ->
                    SecurityErrors.write(json, response, 401, "UNAUTHENTICATED", "Vui lòng đăng nhập để tiếp tục."))
                .accessDeniedHandler((request, response, ex) -> {
                    boolean csrf = ex instanceof MissingCsrfTokenException || ex instanceof InvalidCsrfTokenException;
                    SecurityErrors.write(json, response, 403, csrf ? "CSRF_INVALID" : "FORBIDDEN",
                        csrf ? "Phiên xác thực yêu cầu đã thay đổi. Vui lòng thử lại." : "Bạn không có quyền thực hiện thao tác này.");
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .build();
    }
}
