package ac.mju.contractcheck.domain;

/** 판매하는 이용권 상품. {@code active}가 false이면 목록에 나오지 않고 주문할 수 없다. */
public record Product(long id, String name, int credits, int price, boolean active) {}
