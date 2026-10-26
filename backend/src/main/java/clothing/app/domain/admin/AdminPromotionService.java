package clothing.app.domain.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.admin.dto.AdminPromotionRequests.CreatePromotionRequest;
import clothing.app.domain.admin.dto.AdminPromotionRequests.UpdatePromotionRequest;
import clothing.app.domain.admin.dto.AdminPromotionResponse;
import clothing.app.domain.promotion.PromotionEntity;
import clothing.app.domain.promotion.PromotionRepository;
import clothing.app.domain.promotion.PromotionService;

/**
 * Admin promotion management (PROJECT_SPEC §7, §6: "Manage promotions if
 * included").
 *
 * <p>A promotion is deactivated, never deleted: order history and any audit row
 * that names the code keep meaning. The type is accepted under the spec's names
 * ({@code PERCENTAGE}/{@code FIXED_AMOUNT}) and stored under the schema's
 * ({@code PERCENT}/{@code FIXED}).
 */
@Service
public class AdminPromotionService {

	private static final int MAX_PERCENT = 100;

	private final PromotionRepository promotionRepository;
	private final AuditService auditService;

	public AdminPromotionService(PromotionRepository promotionRepository,
			AuditService auditService) {
		this.promotionRepository = promotionRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<AdminPromotionResponse> list() {
		return promotionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
			.stream()
			.map(AdminPromotionService::toResponse)
			.toList();
	}

	@Transactional
	public AdminPromotionResponse create(String adminUserId, CreatePromotionRequest request) {
		String code = PromotionService.normalize(request.code());
		if (promotionRepository.existsByCode(code)) {
			throw new ApiException.Validation("That code is already in use",
				Map.of("code", "Already in use"));
		}
		PromotionEntity.Type type = parseType(request.type());
		validateValue(type, request.value());
		validateWindow(request.startsAt(), request.endsAt());

		PromotionEntity promotion = new PromotionEntity();
		promotion.setCode(code);
		promotion.setName(trimToNull(request.name()));
		promotion.setDescription(trimToNull(request.description()));
		promotion.setType(type);
		promotion.setValue(request.value());
		promotion.setMinOrder(request.minOrder() == null ? 0L : request.minOrder());
		promotion.setMaxDiscount(request.maxDiscount());
		promotion.setStartsAt(request.startsAt());
		promotion.setEndsAt(request.endsAt());
		promotion.setIsActive(request.active() == null || request.active());
		promotion = promotionRepository.save(promotion);

		auditService.record(adminUserId, "promotion.create", "PROMOTION", promotion.getId(),
			Map.of("code", code, "type", type.name(), "value", promotion.getValue()));
		return toResponse(promotion);
	}

	@Transactional
	public AdminPromotionResponse update(String adminUserId, String promotionId,
			UpdatePromotionRequest request) {
		PromotionEntity promotion = promotionRepository.findById(promotionId)
			.orElseThrow(() -> new ApiException.NotFound("Promotion not found"));
		Map<String, Object> changed = new LinkedHashMap<>();

		if (request.name() != null) {
			promotion.setName(trimToNull(request.name()));
		}
		if (request.description() != null) {
			promotion.setDescription(trimToNull(request.description()));
		}
		if (request.type() != null) {
			PromotionEntity.Type type = parseType(request.type());
			if (type != promotion.getType()) {
				changed.put("type", pair(promotion.getType().name(), type.name()));
				promotion.setType(type);
			}
		}
		if (request.value() != null && !request.value().equals(promotion.getValue())) {
			validateValue(promotion.getType(), request.value());
			changed.put("value", pair(promotion.getValue(), request.value()));
			promotion.setValue(request.value());
		}
		if (request.minOrder() != null && !request.minOrder().equals(promotion.getMinOrder())) {
			changed.put("minOrder", pair(promotion.getMinOrder(), request.minOrder()));
			promotion.setMinOrder(request.minOrder());
		}
		if (request.maxDiscount() != null) {
			changed.put("maxDiscount", pair(promotion.getMaxDiscount(), request.maxDiscount()));
			promotion.setMaxDiscount(request.maxDiscount());
		}
		if (request.startsAt() != null) {
			promotion.setStartsAt(request.startsAt());
		}
		if (request.endsAt() != null) {
			promotion.setEndsAt(request.endsAt());
		}
		validateWindow(promotion.getStartsAt(), promotion.getEndsAt());
		if (request.active() != null && !request.active().equals(promotion.getIsActive())) {
			changed.put("active", pair(promotion.getIsActive(), request.active()));
			promotion.setIsActive(request.active());
		}

		promotionRepository.save(promotion);
		if (!changed.isEmpty()) {
			auditService.record(adminUserId, "promotion.update", "PROMOTION", promotion.getId(),
				Map.of("code", promotion.getCode(), "changes", changed));
		}
		return toResponse(promotion);
	}

	/* --- rules -------------------------------------------------------------- */

	/** Accepts the spec's names and the schema's names for the same two types. */
	static PromotionEntity.Type parseType(String value) {
		if (!StringUtils.hasText(value)) {
			throw new ApiException.Validation("Type is required", Map.of("type", "Required"));
		}
		return switch (value.trim().toUpperCase(Locale.ROOT)) {
			case "PERCENT", "PERCENTAGE" -> PromotionEntity.Type.PERCENT;
			case "FIXED", "FIXED_AMOUNT" -> PromotionEntity.Type.FIXED;
			default -> throw new ApiException.Validation("Unknown promotion type",
				Map.of("type", "Must be PERCENT or FIXED"));
		};
	}

	private static void validateValue(PromotionEntity.Type type, Long value) {
		if (value == null || value <= 0) {
			throw new ApiException.Validation("Value must be greater than zero",
				Map.of("value", "Must be greater than zero"));
		}
		if (type == PromotionEntity.Type.PERCENT && value > MAX_PERCENT) {
			throw new ApiException.Validation("A percentage cannot exceed 100",
				Map.of("value", "Must be 100 or less for a percentage"));
		}
	}

	private static void validateWindow(Instant startsAt, Instant endsAt) {
		if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
			throw new ApiException.Validation("The end date must be after the start date",
				Map.of("endsAt", "Must be after the start date"));
		}
	}

	private static AdminPromotionResponse toResponse(PromotionEntity promotion) {
		return new AdminPromotionResponse(
			promotion.getId(),
			promotion.getCode(),
			promotion.getName(),
			promotion.getDescription(),
			promotion.getType().name(),
			promotion.getValue() == null ? 0L : promotion.getValue(),
			promotion.getMinOrder() == null ? 0L : promotion.getMinOrder(),
			promotion.getMaxDiscount(),
			promotion.getStartsAt(),
			promotion.getEndsAt(),
			Boolean.TRUE.equals(promotion.getIsActive()));
	}

	private static List<Object> pair(Object before, Object after) {
		List<Object> values = new ArrayList<>(2);
		values.add(before);
		values.add(after);
		return values;
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
