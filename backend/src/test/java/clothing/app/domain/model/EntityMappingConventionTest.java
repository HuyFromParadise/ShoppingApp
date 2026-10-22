package clothing.app.domain.model;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.admin.AuditEventEntity;
import clothing.app.domain.cart.CartEntity;
import clothing.app.domain.cart.CartItemEntity;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.order.OrderEntity;
import clothing.app.domain.order.OrderItemEntity;
import clothing.app.domain.order.OrderStatusHistoryEntity;
import clothing.app.domain.payment.IdempotencyRecordEntity;
import clothing.app.domain.payment.PaymentEntity;
import clothing.app.domain.payment.PaymentEventEntity;
import clothing.app.domain.promotion.PromotionEntity;
import clothing.app.domain.review.ReviewEntity;
import clothing.app.domain.user.RefreshTokenEntity;
import clothing.app.domain.user.UserEntity;
import clothing.app.domain.wishlist.WishlistEntity;
import clothing.app.domain.wishlist.WishlistItemEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JPA entity mapping conventions for step 02:
 * every domain entity extends {@link BaseEntity} and BaseEntity carries the
 * auditing fields (id UUID, created_at, updated_at) required by DESIGN_REFERENCE §4.
 */
class EntityMappingConventionTest {

	private static final List<Class<?>> ENTITIES = List.of(
		UserEntity.class, RefreshTokenEntity.class, AddressEntity.class,
		CategoryEntity.class, ProductEntity.class, ProductImageEntity.class, ProductVariantEntity.class,
		CartEntity.class, CartItemEntity.class,
		OrderEntity.class, OrderItemEntity.class, OrderStatusHistoryEntity.class,
		PaymentEntity.class, PaymentEventEntity.class, IdempotencyRecordEntity.class,
		AuditEventEntity.class, WishlistEntity.class, WishlistItemEntity.class, PromotionEntity.class, ReviewEntity.class
	);

	@Test
	void everyDomainEntityExtendsBaseEntity() {
		for (Class<?> entity : ENTITIES) {
			assertThat(entity.isAnnotationPresent(jakarta.persistence.Entity.class))
				.as("%s must be a JPA entity", entity.getSimpleName())
				.isTrue();
			assertThat(BaseEntity.class.isAssignableFrom(entity))
				.as("%s must extend BaseEntity", entity.getSimpleName())
				.isTrue();
		}
	}

	@Test
	void baseEntityDeclaresAuditingFields() {
		Field id = getField(BaseEntity.class, "id");
		Field createdAt = getField(BaseEntity.class, "createdAt");
		Field updatedAt = getField(BaseEntity.class, "updatedAt");

		assertThat(id.getType()).isEqualTo(String.class);
		assertThat(id.isAnnotationPresent(jakarta.persistence.Id.class)).isTrue();
		assertThat(createdAt.getType()).isEqualTo(java.time.Instant.class);
		assertThat(updatedAt.getType()).isEqualTo(java.time.Instant.class);
		assertThat(createdAt.isAnnotationPresent(org.springframework.data.annotation.CreatedDate.class)).isTrue();
		assertThat(updatedAt.isAnnotationPresent(org.springframework.data.annotation.LastModifiedDate.class)).isTrue();
	}

	private static Field getField(Class<?> clazz, String name) {
		try {
			return clazz.getDeclaredField(name);
		} catch (NoSuchFieldException e) {
			throw new AssertionError("BaseEntity missing field: " + name, e);
		}
	}
}
