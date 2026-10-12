package li.figo.helloworld;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
class AdminController {
    private final AppUserRepository users;
    private final GroceryStoreRepository stores;
    AdminController(AppUserRepository users,GroceryStoreRepository stores) { this.users=users; this.stores=stores; }

    record UserView(Long id,String email,boolean approved,boolean admin) {}

    @GetMapping("/users")
    List<UserView> users() {
        return users.findAll().stream().map(u -> new UserView(u.id,u.email,u.isApproved(),u.isAdmin())).toList();
    }

    @PostMapping("/users/{id}/approve")
    Map<String,String> approve(@PathVariable Long id) {
        AppUser user=users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));
        user.approved=true;
        if(stores.findByOwnerEmailIgnoreCaseOrderByCreatedAtAsc(user.email).isEmpty()) {
            GroceryStore store=new GroceryStore();store.name="My Store";store.owner=user;stores.save(store);
        }
        return Map.of("message","Account approved.");
    }
}
