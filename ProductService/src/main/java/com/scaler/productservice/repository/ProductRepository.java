package com.scaler.productservice.repository;

import com.scaler.productservice.model.Product;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Cache individual product lookups.
     * Key  : the product id (e.g. "product::1")
     * TTL  : 30 min (configured in CacheConfig)
     * Evict: ProductServiceImpl evicts this key on every update / delete.
     */
    @Override
    @Cacheable(value = "product", key = "#id")
    Optional<Product> findById(Long id);

    /**
     * Cache the full (unfiltered) product list.
     * Key  : literal "all"
     * TTL  : 10 min (configured in CacheConfig)
     * Evict: ProductServiceImpl evicts this key on every create / update / delete.
     *
     * Note: @SQLRestriction("deleted_at IS NULL") on Product is applied at the
     * Hibernate level before the result is cached, so soft-deleted products are
     * never stored in this cache entry.
     */
    @Override
    @Cacheable(value = "products", key = "'all'")
    List<Product> findAll();
}
