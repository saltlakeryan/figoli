package li.figo.helloworld;

import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroceryControllerTest {
    @Test
    @SuppressWarnings("unchecked")
    void checkedPatchUsesPersistedStoreIdWhenLazyAssociationIsNull() {
        AppUserRepository users = mock(AppUserRepository.class);
        StoreController storeController = mock(StoreController.class);
        EntityManager db = mock(EntityManager.class);
        GroceryController controller = new GroceryController(users, storeController);
        ReflectionTestUtils.setField(controller, "db", db);

        GroceryStore store = new GroceryStore();
        store.id = 1L;
        ListEntry entry = new ListEntry();
        entry.id = 23L;
        entry.list = new ShoppingList();
        entry.list.owner = null;
        entry.list.store = null; // Reproduce the inconsistent lazy association observed in production.
        entry.item = new GroceryItem();
        entry.item.id = 42L;
        entry.item.name = "Frozen Corn";
        when(db.find(ListEntry.class, 23L)).thenReturn(entry);
        String listScopeSql = "select l.store_id,u.email from shopping_lists l left join app_users u on u.id=l.owner_id where l.id=(select list_id from shopping_list_entries where id=:entryId)";
        when(db.createNativeQuery(listScopeSql))
                .thenReturn(mock(Query.class));
        Query storeIdQuery = db.createNativeQuery(listScopeSql);
        when(storeIdQuery.setParameter("entryId", 23L)).thenReturn(storeIdQuery);
        when(storeIdQuery.getResultStream()).thenReturn(Stream.<Object>of((Object)new Object[]{1L, "shopper@example.test"}));
        when(db.createQuery(any(String.class), eq(StoreItemPlacement.class))).thenReturn(mock(TypedQuery.class));
        TypedQuery<StoreItemPlacement> placements = db.createQuery(
                "select distinct p from StoreItemPlacement p left join fetch p.aisle left join fetch p.categories where p.store.id=:storeId and p.item.id=:itemId",
                StoreItemPlacement.class);
        when(placements.setParameter(eq("storeId"), anyLong())).thenReturn(placements);
        when(placements.setParameter(eq("itemId"), anyLong())).thenReturn(placements);
        when(placements.getResultStream()).thenReturn(Stream.empty());
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("shopper@example.test");
        when(storeController.ownedStore(1L, auth)).thenReturn(store);

        ResponseEntity<?> response = controller.setChecked(1L, 23L, new GroceryController.CheckedInput(true), auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, entry.checked);
    }
}
