package li.figo.helloworld;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AdminCliRunner implements ApplicationRunner {
    private final Environment environment;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    AdminCliRunner(Environment environment,AppUserRepository users,PasswordEncoder passwords) {
        this.environment=environment;this.users=users;this.passwords=passwords;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String command=environment.getProperty("figo.admin-command");
        if(command==null || command.isBlank()) return;
        String email=environment.getProperty("figo.email");
        if(email==null || email.isBlank()) throw new IllegalArgumentException("Pass --figo.email=<email>.");
        final String normalizedEmail=email.trim().toLowerCase(Locale.ROOT);
        if("grant-admin".equals(command)) {
            AppUser user=users.findByEmailIgnoreCase(normalizedEmail).orElseThrow(() -> new IllegalArgumentException("No account exists for "+normalizedEmail+"."));
            user.administrator=true;user.approved=true;
            users.saveAndFlush(user);
            System.out.println("Admin access enabled for "+normalizedEmail+".");
            return;
        }
        if(!"create-admin".equals(command)) throw new IllegalArgumentException("Use --figo.admin-command=create-admin or grant-admin.");
        if(users.findByEmailIgnoreCase(normalizedEmail).isPresent()) throw new IllegalArgumentException("That account already exists; use grant-admin to promote it.");
        Console console=System.console();
        if(console==null) throw new IllegalStateException("An interactive terminal is required to enter the new password. Run the command with docker compose exec -it.");
        char[] password=console.readPassword("New admin password (12+ characters): ");
        char[] confirmation=console.readPassword("Confirm password: ");
        try {
            if(!Arrays.equals(password,confirmation)) throw new IllegalArgumentException("Passwords did not match.");
            int bytes=new String(password).getBytes(StandardCharsets.UTF_8).length;
            if(password.length<12 || bytes>72) throw new IllegalArgumentException("Password must be at least 12 characters and no more than 72 UTF-8 bytes.");
            AppUser user=new AppUser(normalizedEmail,passwords.encode(new String(password)));
            user.approved=true;user.administrator=true;
            users.saveAndFlush(user);
            System.out.println("Admin account created for "+normalizedEmail+".");
        } finally {
            Arrays.fill(password,'\0');Arrays.fill(confirmation,'\0');
        }
    }
}
