package ac.mju.contractcheck.dto.payment;

import ac.mju.contractcheck.domain.Product;

public record ProductItem(long id, String name, int credits, int price) {
    public static ProductItem from(Product product) {
        return new ProductItem(product.id(), product.name(), product.credits(), product.price());
    }
}
