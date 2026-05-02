package com.scaler.productservice.specification;

import com.scaler.productservice.model.Category;
import com.scaler.productservice.model.Product;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class ProductSpecification {

    public static Specification<Product> titleContains(String title) {
        return (root, query, cb) -> {
            if (title == null || title.isBlank()) return cb.conjunction();
            return cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
        };
    }

    public static Specification<Product> categoryEquals(String categoryName) {
        return (root, query, cb) -> {
            if (categoryName == null || categoryName.isBlank()) return cb.conjunction();
            var join = root.<Product, Category>join("category", JoinType.INNER);
            return cb.equal(cb.lower(join.get("name")), categoryName.toLowerCase());
        };
    }

    public static Specification<Product> priceGreaterThanOrEqual(Double min) {
        return (root, query, cb) -> {
            if (min == null) return cb.conjunction();
            return cb.greaterThanOrEqualTo(root.get("price"), min);
        };
    }

    public static Specification<Product> priceLessThanOrEqual(Double max) {
        return (root, query, cb) -> {
            if (max == null) return cb.conjunction();
            return cb.lessThanOrEqualTo(root.get("price"), max);
        };
    }

    public static Specification<Product> buildFrom(String title, String category, Double min, Double max) {
        return Specification.where(titleContains(title))
                .and(categoryEquals(category))
                .and(priceGreaterThanOrEqual(min))
                .and(priceLessThanOrEqual(max));
    }
}
