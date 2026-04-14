package dev.jazzybyte.onseoul.repository;

import dev.jazzybyte.onseoul.domain.ServiceChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceChangeLogRepository extends JpaRepository<ServiceChangeLog, Long> {

    List<ServiceChangeLog> findAllByCollectionId(Long collectionId);

    List<ServiceChangeLog> findAllByServiceIdOrderByChangedAtDesc(String serviceId);
}
