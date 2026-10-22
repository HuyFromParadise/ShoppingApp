package clothing.app.domain.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "orders")
public class OrderEntity extends BaseEntity {

	@Column(name = "order_number", length = 24, nullable = false, unique = true)
	private String orderNumber;

	@Column(name = "user_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String userId;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "payment_status", nullable = false)
	@Enumerated(EnumType.STRING)
	private PaymentStatus paymentStatus;

	@Column(name = "subtotal", nullable = false)
	private Long subtotal;

	@Column(name = "discount_total", nullable = false)
	private Long discountTotal = 0L;

	@Column(name = "shipping_total", nullable = false)
	private Long shippingTotal = 0L;

	@Column(name = "grand_total", nullable = false)
	private Long grandTotal;

	@Column(name = "currency", length = 3, columnDefinition = "char(3)", nullable = false)
	private String currency = "VND";

	@Column(name = "ship_recipient_name", length = 100)
	private String shipRecipientName;

	@Column(name = "ship_phone", length = 30)
	private String shipPhone;

	@Column(name = "ship_address_line1", length = 255)
	private String shipAddressLine1;

	@Column(name = "ship_address_line2", length = 255)
	private String shipAddressLine2;

	@Column(name = "ship_ward", length = 100)
	private String shipWard;

	@Column(name = "ship_district", length = 100)
	private String shipDistrict;

	@Column(name = "ship_city_or_province", length = 100)
	private String shipCityOrProvince;

	@Column(name = "ship_postal_code", length = 20)
	private String shipPostalCode;

	@Column(name = "shipping_method_code", length = 40)
	private String shippingMethodCode;

	@Column(name = "shipping_method_name", length = 120)
	private String shippingMethodName;

	@Column(name = "customer_note", length = 500)
	private String customerNote;

	@Column(name = "idempotency_key", length = 80)
	private String idempotencyKey;

	@Column(name = "placed_at")
	private Instant placedAt;

	public String getOrderNumber() {
		return orderNumber;
	}

	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public PaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public void setPaymentStatus(PaymentStatus paymentStatus) {
		this.paymentStatus = paymentStatus;
	}

	public Long getSubtotal() {
		return subtotal;
	}

	public void setSubtotal(Long subtotal) {
		this.subtotal = subtotal;
	}

	public Long getDiscountTotal() {
		return discountTotal;
	}

	public void setDiscountTotal(Long discountTotal) {
		this.discountTotal = discountTotal;
	}

	public Long getShippingTotal() {
		return shippingTotal;
	}

	public void setShippingTotal(Long shippingTotal) {
		this.shippingTotal = shippingTotal;
	}

	public Long getGrandTotal() {
		return grandTotal;
	}

	public void setGrandTotal(Long grandTotal) {
		this.grandTotal = grandTotal;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getShipRecipientName() {
		return shipRecipientName;
	}

	public void setShipRecipientName(String shipRecipientName) {
		this.shipRecipientName = shipRecipientName;
	}

	public String getShipPhone() {
		return shipPhone;
	}

	public void setShipPhone(String shipPhone) {
		this.shipPhone = shipPhone;
	}

	public String getShipAddressLine1() {
		return shipAddressLine1;
	}

	public void setShipAddressLine1(String shipAddressLine1) {
		this.shipAddressLine1 = shipAddressLine1;
	}

	public String getShipAddressLine2() {
		return shipAddressLine2;
	}

	public void setShipAddressLine2(String shipAddressLine2) {
		this.shipAddressLine2 = shipAddressLine2;
	}

	public String getShipWard() {
		return shipWard;
	}

	public void setShipWard(String shipWard) {
		this.shipWard = shipWard;
	}

	public String getShipDistrict() {
		return shipDistrict;
	}

	public void setShipDistrict(String shipDistrict) {
		this.shipDistrict = shipDistrict;
	}

	public String getShipCityOrProvince() {
		return shipCityOrProvince;
	}

	public void setShipCityOrProvince(String shipCityOrProvince) {
		this.shipCityOrProvince = shipCityOrProvince;
	}

	public String getShipPostalCode() {
		return shipPostalCode;
	}

	public void setShipPostalCode(String shipPostalCode) {
		this.shipPostalCode = shipPostalCode;
	}

	public String getShippingMethodCode() {
		return shippingMethodCode;
	}

	public void setShippingMethodCode(String shippingMethodCode) {
		this.shippingMethodCode = shippingMethodCode;
	}

	public String getShippingMethodName() {
		return shippingMethodName;
	}

	public void setShippingMethodName(String shippingMethodName) {
		this.shippingMethodName = shippingMethodName;
	}

	public String getCustomerNote() {
		return customerNote;
	}

	public void setCustomerNote(String customerNote) {
		this.customerNote = customerNote;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public Instant getPlacedAt() {
		return placedAt;
	}

	public void setPlacedAt(Instant placedAt) {
		this.placedAt = placedAt;
	}

	public enum Status {
		PENDING_PAYMENT, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED
	}

	public enum PaymentStatus {
		UNPAID, PENDING, PAID, FAILED, REFUNDED
	}
}
