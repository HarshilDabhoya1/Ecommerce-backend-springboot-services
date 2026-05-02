package com.scaler.productservice.service;

import com.scaler.productservice.dto.ProductSearchCriteria;
import com.scaler.productservice.exception.CategoryNotFoundException;
import com.scaler.productservice.model.Category;
import com.scaler.productservice.model.Product;
import com.scaler.productservice.repository.CategoryRepository;
import com.scaler.productservice.repository.ProductRepository;
import com.scaler.productservice.specification.ProductSpecification;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Qualifier("productServiceImpl")
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // ── Read — cache hits are handled by @Cacheable on ProductRepository ───────

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public Page<Product> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        Specification<Product> spec = ProductSpecification.buildFrom(
                criteria.getTitle(), criteria.getCategory(),
                criteria.getMinPrice(), criteria.getMaxPrice());
        return productRepository.findAll(spec, pageable);
    }

    // ── Write — evict stale cache entries after every mutation ─────────────────

    /**
     * New product: evict only "products::all" (the individual-product cache
     * has no entry for this id yet, so nothing to evict there).
     */
    @Override
    @CacheEvict(value = "products", key = "'all'")
    public Product createProduct(String title, double price, String description,
                                 Long categoryId, String imageUrl, int stockQuantity) {
        Product product = new Product();
        product.setTitle(title);
        product.setPrice(price);
        product.setDescription(description);
        product.setCategory(resolveCategory(categoryId));
        product.setImageUrl(imageUrl);
        product.setStockQuantity(stockQuantity);
        return productRepository.save(product);
    }

    /**
     * Product updated: evict the individual entry AND the full-list snapshot
     * so both getProductById and getAllProducts return fresh data.
     */
    @Override
    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#id"),
        @CacheEvict(value = "products", key = "'all'")
    })
    public Optional<Product> updateProduct(Long id, String title, double price, String description,
                                           Long categoryId, String imageUrl, int stockQuantity) {
        return productRepository.findById(id).map(existing -> {
            existing.setTitle(title);
            existing.setPrice(price);
            existing.setDescription(description);
            existing.setCategory(resolveCategory(categoryId));
            existing.setImageUrl(imageUrl);
            existing.setStockQuantity(stockQuantity);
            return productRepository.save(existing);
        });
    }

    /**
     * Stock change: the product's stockQuantity changed, so evict both caches.
     */
    @Override
    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#id"),
        @CacheEvict(value = "products", key = "'all'")
    })
    public Optional<Product> updateStock(Long id, int quantity) {
        return productRepository.findById(id).map(product -> {
            product.setStockQuantity(quantity);
            return productRepository.save(product);
        });
    }

    /**
     * Soft delete: stamp deletedAt, then evict both caches.
     * @SQLRestriction ensures the deleted product won't appear in future DB
     * queries, but we still need to purge the stale cached snapshots.
     */
    @Override
    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#id"),
        @CacheEvict(value = "products", key = "'all'")
    })
    public boolean deleteProduct(Long id) {
        return productRepository.findById(id).map(product -> {
            product.setDeletedAt(Instant.now());
            productRepository.save(product);
            return true;
        }).orElse(false);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
