package dev.jazzybyte.onseoul.repository;

import dev.jazzybyte.onseoul.domain.DataSourceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceCatalogRepository extends JpaRepository<DataSourceCatalog, Long> {

    List<DataSourceCatalog> findAllByActiveTrue();
}
