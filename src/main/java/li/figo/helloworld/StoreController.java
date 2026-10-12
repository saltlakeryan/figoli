package li.figo.helloworld;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stores")
@Transactional
class StoreController {
    private final GroceryStoreRepository stores;
    private final AppUserRepository users;

    StoreController(GroceryStoreRepository stores, AppUserRepository users) { this.stores=stores; this.users=users; }
    record StoreView(Long id, String name) {}
    record StoreInput(String name) {}

    @GetMapping
    List<StoreView> stores(Authentication auth) {
        return stores.findByOwnerEmailIgnoreCaseOrderByCreatedAtAsc(auth.getName()).stream().map(s -> new StoreView(s.id,s.name)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StoreView create(@RequestBody StoreInput input, Authentication auth) {
        if (input.name()==null || input.name().isBlank() || input.name().trim().length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Store name is required (up to 100 characters).");
        GroceryStore store=new GroceryStore(); store.name=input.name().trim();
        store.owner=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        stores.save(store);
        return new StoreView(store.id,store.name);
    }

    GroceryStore ownedStore(Long id, Authentication auth) {
        return stores.findByIdAndOwnerEmailIgnoreCase(id,auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found."));
    }
}
