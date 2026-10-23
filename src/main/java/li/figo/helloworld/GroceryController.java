package li.figo.helloworld;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    record ItemView(Long id, String name, Long aisleId, String aisleName, Integer aislePercent, List<CategoryView> categories, boolean canDelete) {}
    record EntryView(Long id, Long itemId, String name, String quantity, boolean checked, Long aisleId, String aisleName, Integer aislePercent, List<CategoryView> categories) {}
    record ListView(Long id, String name, List<EntryView> entries) {}
    record CategoryInput(String name) {}
    record AisleInput(String name, int x, int y, int length, String orientation, List<String> categoryNames) {}
    record ItemInput(String name, Long aisleId, Integer aislePercent, List<Long> categoryIds) {}
    record ItemPlacementInput(Long aisleId, Integer aislePercent) {}
    record BulkItemsInput(String lines) {}
    record BulkItemsResult(int imported,List<String> skipped) {}
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
        validateAisleInput(in);
        Aisle a = new Aisle(); a.store=store; a.name=required(in.name()); a.x=in.x(); a.y=in.y(); a.length=in.length();
        a.orientation = "vertical".equalsIgnoreCase(in.orientation()) ? "vertical" : "horizontal";
        a.categories.addAll(resolveOrCreateCategories(in.categoryNames(),store)); db.persist(a); db.flush(); return aisleView(a);
    }
    @PutMapping("/aisles/{id}") AisleView updateAisle(@PathVariable Long storeId,@PathVariable Long id,@RequestBody AisleInput in,Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        validateAisleInput(in);
        Aisle aisle=db.createQuery("select distinct a from Aisle a left join fetch a.categories where a.id=:id and a.store.id=:storeId",Aisle.class)
                .setParameter("id",id).setParameter("storeId",storeId).getResultStream().findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Aisle not found in this store."));
        aisle.name=required(in.name()); aisle.x=in.x(); aisle.y=in.y(); aisle.length=in.length();
        aisle.orientation="vertical".equalsIgnoreCase(in.orientation())?"vertical":"horizontal";
        aisle.categories.clear();
        aisle.categories.addAll(resolveOrCreateCategories(in.categoryNames(),store));
        db.flush();
        return aisleView(aisle);
    }
    @DeleteMapping("/aisles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAisle(@PathVariable Long storeId,@PathVariable Long id,Authentication auth) {
        requireStore(storeId,auth);
        Aisle aisle=db.createQuery("select a from Aisle a where a.id=:id and a.store.id=:storeId",Aisle.class)
                .setParameter("id",id).setParameter("storeId",storeId).getResultStream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Aisle not found in this store."));
        List<StoreItemPlacement> assignedItems=db.createQuery("select p from StoreItemPlacement p where p.aisle.id=:aisleId and p.store.id=:storeId",StoreItemPlacement.class)
                .setParameter("aisleId",id).setParameter("storeId",storeId).getResultList();
        for(StoreItemPlacement placement:assignedItems) { placement.aisle=null; placement.aislePercent=null; }
        if(hasColumn("grocery_items","aisle_id")) {
            db.createNativeQuery("update grocery_items set aisle_id=null, aisle_percent=null where aisle_id=:aisleId")
                    .setParameter("aisleId",id).executeUpdate();
        }
        aisle.categories.clear();
        db.remove(aisle);
    }
    @GetMapping("/items") List<ItemView> items(@PathVariable Long storeId, Authentication auth) {
        requireStore(storeId,auth);
        List<GroceryItem> items=db.createQuery("select i from GroceryItem i left join fetch i.owner where not exists (select other.id from GroceryItem other where lower(other.name)=lower(i.name) and other.id<i.id) order by lower(i.name),i.id",GroceryItem.class).getResultList();
        Map<Long,StoreItemPlacement> placements=placementsByItem(storeId);
        return items.stream().map(i->itemView(i,placements.get(i.id),auth)).toList();
    }
    @PostMapping("/items") ItemView createItem(@PathVariable Long storeId, @RequestBody ItemInput in, Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        String name=required(in.name());
        GroceryItem i=findItemByName(name.toLowerCase(Locale.ROOT));
        if(i==null) { i=new GroceryItem(); i.owner=currentUser(auth); i.name=name; db.persist(i); db.flush(); }
        else if(findPlacement(storeId,i.id)!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"This item is already set up for this store.");
        StoreItemPlacement placement=new StoreItemPlacement(); placement.store=store; placement.item=i;
        if (in.aisleId()!=null) placement.aisle=db.find(Aisle.class,in.aisleId());
        if (in.aisleId()!=null && placement.aisle==null) throw bad("Aisle not found.");
        if (placement.aisle!=null && !placement.aisle.store.id.equals(storeId)) throw bad("Aisle not found in this store.");
        if (in.aislePercent()!=null && (in.aislePercent()<0 || in.aislePercent()>100)) throw bad("Aisle position must be between 0 and 100.");
        placement.aislePercent=in.aislePercent(); placement.categories.addAll(resolveCategories(in.categoryIds(),storeId)); db.persist(placement); db.flush();
        return itemView(i,placement,auth);
    }
    @PutMapping("/items/{id}/placement") ItemView assignItemPlacement(@PathVariable Long storeId,@PathVariable Long id,@RequestBody ItemPlacementInput in,Authentication auth) {
        GroceryStore store=requireStore(storeId,auth);
        if(in==null) throw bad("Choose an aisle or leave it unassigned.");
        if(in.aislePercent()!=null&&(in.aislePercent()<0||in.aislePercent()>100)) throw bad("Aisle position must be between 0 and 100.");
        GroceryItem item=db.find(GroceryItem.class,id);
        if(item==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found in the shared catalog.");
        Aisle aisle=null;
        if(in.aisleId()!=null) aisle=db.createQuery("select a from Aisle a where a.id=:aisleId and a.store.id=:storeId",Aisle.class)
                .setParameter("aisleId",in.aisleId()).setParameter("storeId",storeId).getResultStream().findFirst()
                .orElseThrow(()->bad("Aisle not found in this store."));
        StoreItemPlacement placement=findPlacement(storeId,id);
        if(placement==null) { placement=new StoreItemPlacement(); placement.store=store; placement.item=item; db.persist(placement); }
        placement.aisle=aisle;
        placement.aislePercent=aisle==null?null:in.aislePercent();
        db.flush();
        return itemView(item,placement,auth);
    }
    @PostMapping("/items/import") BulkItemsResult importItems(@PathVariable Long storeId,@RequestBody BulkItemsInput in,Authentication auth) {
        requireStore(storeId,auth);
        if(in==null || in.lines()==null) throw bad("Paste one item name per line.");
        String[] lines=in.lines().split("\\R",-1);
        if(lines.length>500) throw bad("Import up to 500 lines at a time.");
        Set<String> seen=new HashSet<>();
        List<String> skipped=new ArrayList<>();
        int imported=0;
        for(int index=0;index<lines.length;index++) {
            String name=lines[index].trim();
            if(name.isEmpty()) continue;
            if(name.length()>100) throw bad("Item names must be 100 characters or fewer (line "+(index+1)+").");
            String key=name.toLowerCase(Locale.ROOT);
            if(!seen.add(key)) { skipped.add(name); continue; }
            if(findItemByName(key)!=null) { skipped.add(name); continue; }
            GroceryItem item=new GroceryItem(); item.name=name; item.owner=currentUser(auth); db.persist(item); imported++;
        }
        db.flush();
        return new BulkItemsResult(imported,skipped);
    }
    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@PathVariable Long storeId,@PathVariable Long id,Authentication auth) {
        requireStore(storeId,auth);
        GroceryItem item=db.createQuery("select i from GroceryItem i where i.id=:id and i.owner.email=:email",GroceryItem.class)
                .setParameter("id",id).setParameter("email",auth.getName()).getResultStream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found."));
        Long entryCount=db.createQuery("select count(e) from ListEntry e where e.item.id=:itemId",Long.class)
                .setParameter("itemId",id).getSingleResult();
        if(entryCount>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This item is on a shopping list. Remove it from the list before deleting it from the catalog.");
        List<StoreItemPlacement> placements=db.createQuery("select distinct p from StoreItemPlacement p left join fetch p.categories where p.item.id=:itemId",StoreItemPlacement.class).setParameter("itemId",id).getResultList();
        for(StoreItemPlacement placement:placements) { placement.categories.clear(); db.remove(placement); }
        if(hasTable("item_categories")) db.createNativeQuery("delete from item_categories where item_id=:itemId").setParameter("itemId",id).executeUpdate();
        db.remove(item);
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
        ShoppingList l=findList(id,storeId,auth); db.lock(l,LockModeType.PESSIMISTIC_WRITE); GroceryItem i=db.createQuery("select i from GroceryItem i where i.id=:id",GroceryItem.class).setParameter("id",in.itemId()).getResultStream().findFirst().orElseThrow(()->bad("Item not found in the shared catalog."));
        Long duplicate=db.createQuery("select count(e) from ListEntry e where e.list.id=:listId and lower(e.item.name)=:name",Long.class).setParameter("listId",l.id).setParameter("name",i.name.toLowerCase(Locale.ROOT)).getSingleResult();
        if(duplicate>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This item is already on the list.");
        ListEntry e=new ListEntry(); e.list=l; e.item=i; e.quantity=(in.quantity()==null||in.quantity().isBlank())?"1":in.quantity().trim(); db.persist(e); db.flush(); return listView(l);
    }
    @PatchMapping("/entries/{id}") EntryView setChecked(@PathVariable Long storeId,@PathVariable Long id,@RequestBody CheckedInput in, Authentication auth) {
        ListEntry e=db.find(ListEntry.class,id); if(e==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!belongsTo(e.list,storeId,auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        e.checked=in.checked(); return entryView(e,e.list.store.id);
    }
    @DeleteMapping("/entries/{id}") void deleteEntry(@PathVariable Long storeId,@PathVariable Long id, Authentication auth) { ListEntry e=db.find(ListEntry.class,id); if(e!=null) { if(!belongsTo(e.list,storeId,auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); db.remove(e); } }
    @DeleteMapping("/lists/{id}") void deleteList(@PathVariable Long storeId,@PathVariable Long id, Authentication auth) {
        ShoppingList l=findList(id,storeId,auth); db.createQuery("delete from ListEntry e where e.list.id=:id").setParameter("id",id).executeUpdate(); db.remove(l);
    }

    private String required(String s) { if(s==null||s.isBlank()) throw bad("Name is required."); return s.trim(); }
    private void validateAisleInput(AisleInput in) { if(in==null||in.length()<1||in.x()<1||in.y()<1) throw bad("Aisle position and length must be positive."); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private GroceryStore requireStore(Long storeId,Authentication auth) { return storeController.ownedStore(storeId,auth); }
    private boolean belongsTo(ShoppingList list,Long storeId,Authentication auth) { return list.store!=null && list.store.id.equals(storeId) && list.owner.getUsername().equalsIgnoreCase(auth.getName()) && storeController.ownedStore(storeId,auth)!=null; }
    private ShoppingList findList(Long id,Long storeId, Authentication auth) { requireStore(storeId,auth); ShoppingList l=db.find(ShoppingList.class,id); if(l==null || !belongsTo(l,storeId,auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return l; }
    private Set<Category> resolveCategories(List<Long> ids,Long storeId) { Set<Category> result=new LinkedHashSet<>(); if(ids!=null) for(Long id:ids) { Category c=db.find(Category.class,id); if(c==null || c.store==null || !c.store.id.equals(storeId)) throw bad("Category not found in this store."); result.add(c); } return result; }
    private Set<Category> resolveOrCreateCategories(List<String> names,GroceryStore store) {
        Set<Category> result=new LinkedHashSet<>();
        if(names==null) return result;
        for(String raw:names) {
            if(raw==null||raw.isBlank()) continue;
            String name=raw.trim();
            if(name.length()>60) throw bad("Category names must be 60 characters or fewer.");
            Category category=db.createQuery("select c from Category c where c.store.id=:storeId and lower(c.name)=:name",Category.class)
                    .setParameter("storeId",store.id).setParameter("name",name.toLowerCase(Locale.ROOT)).getResultStream().findFirst().orElse(null);
            if(category==null) { category=new Category(name); category.store=store; db.persist(category); }
            result.add(category);
        }
        return result;
    }
    private CategoryView categoryView(Category c) { return new CategoryView(c.id,c.name); }
    private AisleView aisleView(Aisle a) { return new AisleView(a.id,a.name,a.x,a.y,a.length,a.orientation,a.categories.stream().map(this::categoryView).toList()); }
    private ItemView itemView(GroceryItem i,StoreItemPlacement placement,Authentication auth) { boolean canDelete=i.owner!=null&&i.owner.getUsername()!=null&&i.owner.getUsername().equalsIgnoreCase(auth.getName()); return new ItemView(i.id,i.name,placement==null||placement.aisle==null?null:placement.aisle.id,placement==null||placement.aisle==null?null:placement.aisle.name,placement==null?null:placement.aislePercent,placement==null?List.of():placement.categories.stream().map(this::categoryView).toList(),canDelete); }
    private EntryView entryView(ListEntry e,Long storeId) { GroceryItem i=e.item; StoreItemPlacement placement=findPlacement(storeId,i.id); return new EntryView(e.id,i.id,i.name,e.quantity,e.checked,placement==null||placement.aisle==null?null:placement.aisle.id,placement==null||placement.aisle==null?null:placement.aisle.name,placement==null?null:placement.aislePercent,placement==null?List.of():placement.categories.stream().map(this::categoryView).toList()); }
    private ListView listView(ShoppingList l) {
        List<ListEntry> entries=db.createQuery("select e from ListEntry e join fetch e.item where e.list.id=:id order by e.id",ListEntry.class).setParameter("id",l.id).getResultList();
        return new ListView(l.id,l.name,entries.stream().map(e->entryView(e,l.store.id)).toList());
    }

    private AppUser currentUser(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(); }
    private GroceryItem findItemByName(String normalizedName) { return db.createQuery("select i from GroceryItem i where lower(i.name)=:name order by i.id",GroceryItem.class).setParameter("name",normalizedName).setMaxResults(1).getResultStream().findFirst().orElse(null); }
    private Map<Long,StoreItemPlacement> placementsByItem(Long storeId) {
        List<StoreItemPlacement> found=db.createQuery("select distinct p from StoreItemPlacement p left join fetch p.aisle left join fetch p.categories where p.store.id=:storeId",StoreItemPlacement.class).setParameter("storeId",storeId).getResultList();
        Map<Long,StoreItemPlacement> result=new HashMap<>(); for(StoreItemPlacement placement:found) result.put(placement.item.id,placement); return result;
    }
    private StoreItemPlacement findPlacement(Long storeId,Long itemId) {
        return db.createQuery("select distinct p from StoreItemPlacement p left join fetch p.aisle left join fetch p.categories where p.store.id=:storeId and p.item.id=:itemId",StoreItemPlacement.class).setParameter("storeId",storeId).setParameter("itemId",itemId).getResultStream().findFirst().orElse(null);
    }
    private boolean hasTable(String table) { return (Boolean)db.createNativeQuery("select exists(select 1 from information_schema.tables where table_schema=current_schema() and table_name=:table)").setParameter("table",table).getSingleResult(); }
    private boolean hasColumn(String table,String column) { return (Boolean)db.createNativeQuery("select exists(select 1 from information_schema.columns where table_schema=current_schema() and table_name=:table and column_name=:column)").setParameter("table",table).setParameter("column",column).getSingleResult(); }
}
