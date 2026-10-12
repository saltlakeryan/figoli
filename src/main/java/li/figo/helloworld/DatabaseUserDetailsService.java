package li.figo.helloworld;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class DatabaseUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    DatabaseUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}
