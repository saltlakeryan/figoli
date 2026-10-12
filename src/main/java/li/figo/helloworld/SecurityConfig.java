package li.figo.helloworld;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import java.time.Duration;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean CookieSerializer sessionCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieMaxAge((int) Duration.ofDays(90).getSeconds());
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository contextRepository, CsrfTokenRepository csrfRepository) throws Exception {
        http
            .securityContext(context -> context.securityContextRepository(contextRepository))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login", "/register", "/styles.css", "/app.js", "/api/auth/login", "/api/auth/register", "/api/auth/csrf").permitAll()
                .requestMatchers("/admin", "/admin/**", "/admin.html", "/admin.js", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/market", "/market/**", "/app1", "/app1/**", "/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, cause) -> {
                if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                    response.sendError(401);
                } else {
                    new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, cause);
                }
            }))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
        return http.build();
    }
}
