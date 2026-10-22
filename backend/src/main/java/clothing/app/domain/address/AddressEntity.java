package clothing.app.domain.address;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "addresses")
public class AddressEntity extends BaseEntity {

	@Column(name = "user_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String userId;

	@Column(name = "recipient_name", length = 100, nullable = false)
	private String recipientName;

	@Column(name = "phone", length = 30)
	private String phone;

	@Column(name = "address_line1", length = 255, nullable = false)
	private String addressLine1;

	@Column(name = "address_line2", length = 255)
	private String addressLine2;

	@Column(name = "ward", length = 100)
	private String ward;

	@Column(name = "district", length = 100)
	private String district;

	@Column(name = "city_or_province", length = 100, nullable = false)
	private String cityOrProvince;

	@Column(name = "postal_code", length = 20)
	private String postalCode;

	@Column(name = "is_default", nullable = false)
	private Boolean isDefault = Boolean.FALSE;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public void setRecipientName(String recipientName) {
		this.recipientName = recipientName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getAddressLine1() {
		return addressLine1;
	}

	public void setAddressLine1(String addressLine1) {
		this.addressLine1 = addressLine1;
	}

	public String getAddressLine2() {
		return addressLine2;
	}

	public void setAddressLine2(String addressLine2) {
		this.addressLine2 = addressLine2;
	}

	public String getWard() {
		return ward;
	}

	public void setWard(String ward) {
		this.ward = ward;
	}

	public String getDistrict() {
		return district;
	}

	public void setDistrict(String district) {
		this.district = district;
	}

	public String getCityOrProvince() {
		return cityOrProvince;
	}

	public void setCityOrProvince(String cityOrProvince) {
		this.cityOrProvince = cityOrProvince;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public Boolean getIsDefault() {
		return isDefault;
	}

	public void setIsDefault(Boolean isDefault) {
		this.isDefault = isDefault;
	}
}
