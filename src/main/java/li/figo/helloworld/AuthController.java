package li.figo.helloworld;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    AuthController(AppUserRepository users, PasswordEncoder passwords, AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository) {
        this.users=users; this.passwords=passwords; this.authenticationManager=authenticationManager; this.contextRepository=contextRepository;
    }

    record Credentials(String email, String password) {}

    @GetMapping("/csrf") Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/register")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@RequestBody Credentials credentials) {
        if (credentials.email()==null || credentials.password()==null) throw invalid("Enter an email and password.");
        String email=credentials.email().trim().toLowerCase(Locale.ROOT);
        if (email.length()>254 || !EMAIL.matcher(email).matches()) throw invalid("Enter a valid email address.");
        int passwordBytes=credentials.password().getBytes(StandardCharsets.UTF_8).length;
        if (credentials.password().length()<12 || passwordBytes>72) throw invalid("Use a password of at least 12 characters and no more than 72 UTF-8 bytes.");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists.");
        AppUser account=new AppUser(email,passwords.encode(credentials.password()));
        account.approved=false;
        account.administrator=false;
        users.save(account);
        return Map.of("message","Registration submitted. An admin must approve your account before you can sign in.");
    }

    @PostMapping("/login")
    Map<String, String> login(@RequestBody Credentials credentials, HttpServletRequest request, HttpServletResponse response) {
        if(credentials.email()==null || credentials.password()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        try {
            Authentication authentication=authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email().trim().toLowerCase(Locale.ROOT),credentials.password()));
            request.getSession(true);
            request.changeSessionId();
            SecurityContext context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context,request,response);
            return Map.of("email",authentication.getName());
        } catch (LockedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Your account is waiting for admin approval.");
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        }
    }

    record Identity(String email,boolean admin) {}
    @GetMapping("/me") Identity me(Authentication authentication) {
        AppUser user=users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return new Identity(user.email,user.isAdmin());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request,HttpServletResponse response,Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request,response,authentication);
    }

    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
