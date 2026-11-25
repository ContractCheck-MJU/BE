package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code products}. 테이블 매핑만 하고 업무 규칙은 domain에 둔다. */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String name;

    private int credits;

    private int price;

    private boolean active;

    ProductEntity(Long id, String name, int credits, int price, boolean active) {
        this.id = id;
        this.name = name;
        this.credits = credits;
        this.price = price;
        this.active = active;
    }
}
