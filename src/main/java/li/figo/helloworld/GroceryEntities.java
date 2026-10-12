package li.figo.helloworld;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.AuthorityUtils;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
class AppUser implements UserDetails {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 254) String email;
    @Column(name = "password_hash", nullable = false, length = 100) String passwordHash;
    @Column(name = "approved") Boolean approved;
    @Column(name = "administrator") Boolean administrator;
    protected AppUser() {}
    AppUser(String email, String passwordHash) { this.email=email; this.passwordHash=passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return Boolean.TRUE.equals(administrator)
                ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADMIN")
                : AuthorityUtils.createAuthorityList("ROLE_USER");
    }
    boolean isAdmin() { return Boolean.TRUE.equals(administrator); }
    boolean isApproved() { return !Boolean.FALSE.equals(approved); }
    @Override public boolean isAccountNonLocked() { return isApproved(); }
}

@Entity
@Table(name = "stores")
class GroceryStore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 100) String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) AppUser owner;
    @Column(nullable = false) java.time.Instant createdAt = java.time.Instant.now();
    protected GroceryStore() {}
}

@Entity
@Table(name = "categories")
class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id") GroceryStore store;
    protected Category() {}
    Category(String name) { this.name = name; }
}

@Entity
@Table(name = "aisles")
class Aisle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) String name;
    @Column(name = "grid_x", nullable = false) int x;
    @Column(name = "grid_y", nullable = false) int y;
    @Column(nullable = false) int length;
    @Column(nullable = false) String orientation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id") GroceryStore store;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "aisle_categories", joinColumns = @JoinColumn(name = "aisle_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    Set<Category> categories = new LinkedHashSet<>();
    protected Aisle() {}
}

@Entity
@Table(name = "grocery_items")
class GroceryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id") GroceryStore store;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "aisle_id") Aisle aisle;
    @Column(name = "aisle_percent") Integer aislePercent;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "item_categories", joinColumns = @JoinColumn(name = "item_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    Set<Category> categories = new LinkedHashSet<>();
    protected GroceryItem() {}
}

@Entity
@Table(name = "shopping_lists")
class ShoppingList {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) String name;
    @Column(nullable = false) Instant createdAt = Instant.now();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) AppUser owner;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id") GroceryStore store;
    protected ShoppingList() {}
}

@Entity
@Table(name = "shopping_list_entries")
class ListEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "list_id") ShoppingList list;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "item_id") GroceryItem item;
    @Column(nullable = false) String quantity = "1";
    @Column(nullable = false) boolean checked;
    protected ListEntry() {}
}
