package dev.jazzybyte.onseoul.repository;

import dev.jazzybyte.onseoul.domain.CollectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionHistoryRepository extends JpaRepository<CollectionHistory, Long> {

    List<CollectionHistory> findAllBySourceIdOrderByCollectedAtDesc(Long sourceId);
}
