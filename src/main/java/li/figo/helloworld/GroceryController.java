package li.figo.helloworld;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/stores/{storeId}")
@Transactional
class GroceryController {
    @PersistenceContext EntityManager db;
    private final AppUserRepository users;
    private final StoreController storeController;

    GroceryController(AppUserRepository users, StoreController storeController) { this.users=users; this.storeController=storeController; }

    record CategoryView(Long id, String name) {}
    record AisleView(Long id, String name, int x, int y, int length, String orientation, List<CategoryView> categories) {}
    record ItemView(Long id, String name, Long aisleId, String aisleName, Integer aislePercent, List<CategoryView> categories) {}
    record EntryView(Long id, Long itemId, String name, String quantity, boolean checked, Long aisleId, String aisleName, Integer aislePercent, List<CategoryView> categories) {}
    record ListView(Long id, String name, List<EntryView> entries) {}
    record CategoryInput(String name) {}
    record AisleInput(String name, int x, int y, int length, String orientation, List<Long> categoryIds) {}
    record ItemInput(String name, Long aisleId, Integer aislePercent, List<Long> categoryIds) {}
    record ListInput(String name) {}
    record EntryInput(Long itemId, String quantity) {}
    record CheckedInput(boolean checked) {}

    @GetMapping("/categories") List<CategoryView> categories(@PathVariable Long storeId, Authentication auth) {
        requireStore(storeId,auth);
        return db.createQuery("select c from Category c where c.store.id=:storeId order by c.name", Category.class).setParameter("storeId",storeId).getResultList().stream().map(c -> new CategoryView(c.id,c.name)).toList();
    }
    @PostMapping("/categories") CategoryView createCategory(@PathVariable Long storeId, @RequestBody CategoryInput in, Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        String name = required(in.name());
        Category c = new Category(name); c.store=store; db.persist(c); db.flush(); return new CategoryView(c.id,c.name);
    }
    @GetMapping("/aisles") List<AisleView> aisles(@PathVariable Long storeId, Authentication auth) {
        requireStore(storeId,auth);
        return db.createQuery("select distinct a from Aisle a left join fetch a.categories where a.store.id=:storeId order by a.y,a.x", Aisle.class).setParameter("storeId",storeId).getResultList().stream().map(this::aisleView).toList();
    }
    @PostMapping("/aisles") AisleView createAisle(@PathVariable Long storeId, @RequestBody AisleInput in, Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        if (in.length() < 1 || in.x() < 1 || in.y() < 1) throw bad("Aisle position and length must be positive.");
        Aisle a = new Aisle(); a.store=store; a.name=required(in.name()); a.x=in.x(); a.y=in.y(); a.length=in.length();
        a.orientation = "vertical".equalsIgnoreCase(in.orientation()) ? "vertical" : "horizontal";
        a.categories.addAll(resolveCategories(in.categoryIds(),storeId)); db.persist(a); db.flush(); return aisleView(a);
    }
    @GetMapping("/items") List<ItemView> items(@PathVariable Long storeId, Authentication auth) {
        requireStore(storeId,auth);
        return db.createQuery("select distinct i from GroceryItem i left join fetch i.aisle left join fetch i.categories where i.store.id=:storeId order by i.name", GroceryItem.class).setParameter("storeId",storeId).getResultList().stream().map(this::itemView).toList();
    }
    @PostMapping("/items") ItemView createItem(@PathVariable Long storeId, @RequestBody ItemInput in, Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        GroceryItem i = new GroceryItem(); i.store=store; i.name=required(in.name());
        if (in.aisleId()!=null) i.aisle=db.find(Aisle.class,in.aisleId());
        if (in.aisleId()!=null && i.aisle==null) throw bad("Aisle not found.");
        if (i.aisle!=null && !i.aisle.store.id.equals(storeId)) throw bad("Aisle not found in this store.");
        if (in.aislePercent()!=null && (in.aislePercent()<0 || in.aislePercent()>100)) throw bad("Aisle position must be between 0 and 100.");
        i.aislePercent=in.aislePercent(); i.categories.addAll(resolveCategories(in.categoryIds(),storeId)); db.persist(i); db.flush();
        return itemView(db.createQuery("select i from GroceryItem i left join fetch i.aisle left join fetch i.categories where i.id=:id and i.store.id=:storeId", GroceryItem.class).setParameter("id",i.id).setParameter("storeId",storeId).getSingleResult());
    }
    @GetMapping("/lists") List<ListView> lists(@PathVariable Long storeId, Authentication auth) {
        requireStore(storeId,auth);
        return db.createQuery("select l from ShoppingList l where l.store.id=:storeId and l.owner.email=:email order by l.createdAt desc", ShoppingList.class).setParameter("storeId",storeId).setParameter("email",auth.getName()).getResultList().stream().map(this::listView).toList();
    }
    @PostMapping("/lists") ListView createList(@PathVariable Long storeId, @RequestBody ListInput in, Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        ShoppingList l=new ShoppingList(); l.name=required(in.name()); l.store=store; l.owner=users.findByEmailIgnoreCase(auth.getName()).orElseThrow(); db.persist(l); db.flush(); return new ListView(l.id,l.name,List.of());
    }
    @GetMapping("/lists/{id}") ListView getList(@PathVariable Long storeId,@PathVariable Long id, Authentication auth) { return listView(findList(id,storeId,auth)); }
    @PostMapping("/lists/{id}/entries") ListView addEntry(@PathVariable Long storeId,@PathVariable Long id,@RequestBody EntryInput in, Authentication auth) {
        ShoppingList l=findList(id,storeId,auth); GroceryItem i=db.find(GroceryItem.class,in.itemId()); if(i==null || i.store==null || !i.store.id.equals(storeId)) throw bad("Item not found in this store.");
        ListEntry e=new ListEntry(); e.list=l; e.item=i; e.quantity=(in.quantity()==null||in.quantity().isBlank())?"1":in.quantity().trim(); db.persist(e); db.flush(); return listView(l);
    }
    @PatchMapping("/entries/{id}") EntryView setChecked(@PathVariable Long storeId,@PathVariable Long id,@RequestBody CheckedInput in, Authentication auth) {
        ListEntry e=db.find(ListEntry.class,id); if(e==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!belongsTo(e.list,storeId,auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        e.checked=in.checked(); return entryView(e);
    }
    @DeleteMapping("/entries/{id}") void deleteEntry(@PathVariable Long storeId,@PathVariable Long id, Authentication auth) { ListEntry e=db.find(ListEntry.class,id); if(e!=null) { if(!belongsTo(e.list,storeId,auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); db.remove(e); } }
    @DeleteMapping("/lists/{id}") void deleteList(@PathVariable Long storeId,@PathVariable Long id, Authentication auth) {
        ShoppingList l=findList(id,storeId,auth); db.createQuery("delete from ListEntry e where e.list.id=:id").setParameter("id",id).executeUpdate(); db.remove(l);
    }

    private String required(String s) { if(s==null||s.isBlank()) throw bad("Name is required."); return s.trim(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private GroceryStore requireStore(Long storeId,Authentication auth) { return storeController.ownedStore(storeId,auth); }
    private boolean belongsTo(ShoppingList list,Long storeId,Authentication auth) { return list.store!=null && list.store.id.equals(storeId) && list.owner.getUsername().equalsIgnoreCase(auth.getName()) && storeController.ownedStore(storeId,auth)!=null; }
    private ShoppingList findList(Long id,Long storeId, Authentication auth) { requireStore(storeId,auth); ShoppingList l=db.find(ShoppingList.class,id); if(l==null || !belongsTo(l,storeId,auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return l; }
    private Set<Category> resolveCategories(List<Long> ids,Long storeId) { Set<Category> result=new LinkedHashSet<>(); if(ids!=null) for(Long id:ids) { Category c=db.find(Category.class,id); if(c==null || c.store==null || !c.store.id.equals(storeId)) throw bad("Category not found in this store."); result.add(c); } return result; }
    private CategoryView categoryView(Category c) { return new CategoryView(c.id,c.name); }
    private AisleView aisleView(Aisle a) { return new AisleView(a.id,a.name,a.x,a.y,a.length,a.orientation,a.categories.stream().map(this::categoryView).toList()); }
    private ItemView itemView(GroceryItem i) { return new ItemView(i.id,i.name,i.aisle==null?null:i.aisle.id,i.aisle==null?null:i.aisle.name,i.aislePercent,i.categories.stream().map(this::categoryView).toList()); }
    private EntryView entryView(ListEntry e) { GroceryItem i=e.item; return new EntryView(e.id,i.id,i.name,e.quantity,e.checked,i.aisle==null?null:i.aisle.id,i.aisle==null?null:i.aisle.name,i.aislePercent,i.categories.stream().map(this::categoryView).toList()); }
    private ListView listView(ShoppingList l) {
        List<ListEntry> entries=db.createQuery("select e from ListEntry e join fetch e.item i left join fetch i.aisle left join fetch i.categories where e.list.id=:id order by e.id",ListEntry.class).setParameter("id",l.id).getResultList();
        return new ListView(l.id,l.name,entries.stream().map(this::entryView).toList());
    }
}
