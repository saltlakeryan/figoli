package li.figo.helloworld;

import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
class StoreMigrationService {
    private static final Logger log=LoggerFactory.getLogger(StoreMigrationService.class);
    @PersistenceContext EntityManager db;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void migrateStoreAndItemData() {
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
        if (!stores.isEmpty()) {
            GroceryStore defaultStore = stores.get(0);
            db.createQuery("select c from Category c where c.store is null", Category.class).getResultList().forEach(c -> c.store=defaultStore);
            db.createQuery("select a from Aisle a where a.store is null", Aisle.class).getResultList().forEach(a -> a.store=defaultStore);
            List<ShoppingList> lists = db.createQuery("select l from ShoppingList l where l.store is null", ShoppingList.class).getResultList();
            for (ShoppingList list : lists) {
                list.store = stores.stream().filter(s -> s.owner.id.equals(list.owner.id)).findFirst().orElse(defaultStore);
            }
        }
        db.createNativeQuery("alter table categories drop constraint if exists ukt8o6pivur7nn124jehx7cygw5").executeUpdate();

        if (hasColumn("grocery_items", "store_id")) migrateLegacyItems(stores);
        Long duplicateNames=db.createQuery("select count(i) from GroceryItem i where exists (select other.id from GroceryItem other where lower(other.name)=lower(i.name) and other.id<i.id)",Long.class).getSingleResult();
        if (duplicateNames==0) {
            db.createNativeQuery("create unique index if not exists uq_grocery_items_name_ci on grocery_items (lower(name))").executeUpdate();
        } else {
            log.warn("Not creating the global unique item-name index because {} existing duplicate item name(s) remain; application-level duplicate checks are active.",duplicateNames);
        }
        if (hasTable("shopping_list_entries")) {
            Number duplicateEntries=(Number)db.createNativeQuery("select count(*) from (select list_id,item_id from shopping_list_entries group by list_id,item_id having count(*)>1) duplicates").getSingleResult();
            if (duplicateEntries.longValue()==0) {
                db.createNativeQuery("create unique index if not exists uq_shopping_list_entries_list_item on shopping_list_entries (list_id,item_id)").executeUpdate();
            } else {
                log.warn("Not creating the unique list-item index because {} list/item pair(s) already have duplicate entries; application-level duplicate checks are active.",duplicateEntries.longValue());
            }
        }
    }

    private void migrateLegacyItems(List<GroceryStore> stores) {
        db.createNativeQuery("""
                insert into store_item_placements (store_id,item_id,aisle_id,aisle_percent)
                select store_id,id,aisle_id,aisle_percent from grocery_items
                where store_id is not null
                on conflict (store_id,item_id) do update set
                    aisle_id=coalesce(store_item_placements.aisle_id,excluded.aisle_id),
                    aisle_percent=coalesce(store_item_placements.aisle_percent,excluded.aisle_percent)
                """).executeUpdate();
        if (hasTable("item_categories")) {
            db.createNativeQuery("""
                    insert into store_item_categories (placement_id,category_id)
                    select p.id,ic.category_id from item_categories ic
                    join store_item_placements p on p.item_id=ic.item_id
                    on conflict do nothing
                    """).executeUpdate();
        }
        // Items are shared globally; store-specific data lives in placements. Keep owner_id as creator metadata for delete authorization.
        db.createNativeQuery("update grocery_items set store_id=null,aisle_id=null,aisle_percent=null where store_id is not null or aisle_id is not null or aisle_percent is not null").executeUpdate();
    }

    private boolean hasColumn(String table,String column) {
        return (Boolean) db.createNativeQuery("select exists(select 1 from information_schema.columns where table_schema=current_schema() and table_name=:table and column_name=:column)")
                .setParameter("table",table).setParameter("column",column).getSingleResult();
    }

    private boolean hasTable(String table) {
        return (Boolean) db.createNativeQuery("select exists(select 1 from information_schema.tables where table_schema=current_schema() and table_name=:table)")
                .setParameter("table",table).getSingleResult();
    }
}
