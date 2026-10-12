package li.figo.helloworld;

import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
class StoreMigrationService {
    @PersistenceContext EntityManager db;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void createDefaultStoresAndMoveLegacyData() {
        List<AppUser> activeUsers = db.createQuery("select u from AppUser u where u.approved is null or u.approved=true order by u.id", AppUser.class).getResultList();
        for (AppUser user : activeUsers) {
            Long count = db.createQuery("select count(s) from GroceryStore s where s.owner.id=:ownerId", Long.class).setParameter("ownerId", user.id).getSingleResult();
            if (count == 0) {
                GroceryStore store = new GroceryStore();
                store.name = "My Store";
                store.owner = user;
                db.persist(store);
            }
        }
        db.flush();
        List<GroceryStore> stores = db.createQuery("select s from GroceryStore s order by s.id", GroceryStore.class).getResultList();
        if (stores.isEmpty()) return;
        GroceryStore defaultStore = stores.get(0);
        db.createQuery("select c from Category c where c.store is null", Category.class).getResultList().forEach(c -> c.store=defaultStore);
        db.createQuery("select a from Aisle a where a.store is null", Aisle.class).getResultList().forEach(a -> a.store=defaultStore);
        db.createQuery("select i from GroceryItem i where i.store is null", GroceryItem.class).getResultList().forEach(i -> i.store=defaultStore);
        List<ShoppingList> lists = db.createQuery("select l from ShoppingList l where l.store is null", ShoppingList.class).getResultList();
        for (ShoppingList list : lists) {
            list.store = stores.stream().filter(s -> s.owner.id.equals(list.owner.id)).findFirst().orElse(defaultStore);
        }
        db.createNativeQuery("alter table categories drop constraint if exists ukt8o6pivur7nn124jehx7cygw5").executeUpdate();
    }
}
