package com.medtrace.repo;
import com.medtrace.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface Repos {
    interface Users extends JpaRepository<AppUser, Long> { Optional<AppUser> findByUsername(String u); }
    interface Batches extends JpaRepository<Batch, Long> { Optional<Batch> findByBatchNo(String no); }
    interface Shipments extends JpaRepository<Shipment, Long> {
        List<Shipment> findByBatch(Batch b);
        List<Shipment> findByReceiver(AppUser u);
    }
    interface Recalls extends JpaRepository<Recall, Long> {}
}
