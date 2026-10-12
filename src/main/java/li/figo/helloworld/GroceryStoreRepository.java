package li.figo.helloworld;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GroceryStoreRepository extends JpaRepository<GroceryStore, Long> {
    List<GroceryStore> findByOwnerEmailIgnoreCaseOrderByCreatedAtAsc(String email);
    Optional<GroceryStore> findByIdAndOwnerEmailIgnoreCase(Long id, String email);
}
