package clothing.app.domain.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import clothing.app.common.exception.ApiException;

/**
 * VNPay v2.1.0 skeleton (ADR-007). Only the parts that are dictated by the
 * provider's published protocol are implemented: the sorted query string, the
 * HMAC-SHA512 signature, and the fixed {@code vnp_*} parameter names.
 *
 * <p>No credentials are invented. Until {@code VNPAY_TMN_CODE},
 * {@code VNPAY_HASH_SECRET} and {@code VNPAY_PAY_URL} are configured, creating a
 * payment throws {@code PROVIDER_NOT_CONFIGURED} and every webhook is rejected,
 * so a misconfigured deployment cannot be talked into marking orders paid.
 */
@Component
public class VnPayGateway implements PaymentGateway {

	public static final String PROVIDER = "VNPAY";

	private static final String VERSION = "2.1.0";
	private static final String COMMAND = "pay";
	private static final String ORDER_TYPE = "other";
	private static final String LOCALE = "vn";
	private static final String SUCCESS_CODE = "00";
	private static final String HMAC_SHA512 = "HmacSHA512";
	private static final DateTimeFormatter VNPAY_TIME =
		DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

	private final String tmnCode;
	private final String hashSecret;
	private final String payUrl;
	private final long expireMinutes;

	public VnPayGateway(
			@Value("${VNPAY_TMN_CODE:}") String tmnCode,
			@Value("${VNPAY_HASH_SECRET:}") String hashSecret,
			@Value("${VNPAY_PAY_URL:}") String payUrl,
			@Value("${app.payment.vnpay.expire-minutes:15}") long expireMinutes) {
		this.tmnCode = blankToNull(tmnCode);
		this.hashSecret = blankToNull(hashSecret);
		this.payUrl = blankToNull(payUrl);
		this.expireMinutes = expireMinutes;
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public boolean isConfigured() {
		return tmnCode != null && hashSecret != null && payUrl != null;
	}

	@Override
	public PaymentInitiation create(PaymentRequest request) {
		if (!isConfigured()) {
			throw new ApiException.ProviderNotConfigured();
		}
		if (request.returnUrl() == null || request.returnUrl().isBlank()) {
			throw new ApiException.Validation("A return URL is required for VNPay");
		}

		Instant now = Instant.now();
		Map<String, String> params = new LinkedHashMap<>();
		params.put("vnp_Version", VERSION);
		params.put("vnp_Command", COMMAND);
		params.put("vnp_TmnCode", tmnCode);
		params.put("vnp_Locale", LOCALE);
		params.put("vnp_CurrCode", request.currency());
		params.put("vnp_TxnRef", request.orderNumber());
		params.put("vnp_OrderInfo", "Thanh toan don hang " + request.orderNumber());
		params.put("vnp_OrderType", ORDER_TYPE);
		// VNPay amounts are in the currency's smallest unit, which for VND is the
		// đồng: our minor units are already what it expects.
		params.put("vnp_Amount", Long.toString(request.amountMinor()));
		params.put("vnp_ReturnUrl", request.returnUrl());
		params.put("vnp_IpAddr", request.clientIp() == null ? "127.0.0.1" : request.clientIp());
		params.put("vnp_CreateDate", VNPAY_TIME.format(now));
		params.put("vnp_ExpireDate", VNPAY_TIME.format(now.plusSeconds(expireMinutes * 60)));

		String query = canonicalQuery(params);
		String signed = query + "&vnp_SecureHash=" + hmacSha512(hashSecret, query);
		return new PaymentInitiation(PROVIDER, request.orderNumber(),
			payUrl + "?" + signed, Map.of());
	}

	/**
	 * Verifies a webhook/return callback: the signature must match and the
	 * response code must be a success. Anything else is a failed payment.
	 *
	 * @throws ApiException.ProviderNotConfigured when the provider has no secret
	 */
	public Verification verify(Map<String, String> params) {
		if (!isConfigured()) {
			throw new ApiException.ProviderNotConfigured();
		}
		String provided = params.get("vnp_SecureHash");
		if (provided == null || provided.isBlank()) {
			throw new ApiException.Forbidden("Missing VNPay signature");
		}
		Map<String, String> signedParams = new TreeMap<>(params);
		signedParams.remove("vnp_SecureHash");
		signedParams.remove("vnp_SecureHashType");
		String expected = hmacSha512(hashSecret, canonicalQuery(signedParams));
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				provided.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
			throw new ApiException.Forbidden("Invalid VNPay signature");
		}
		return new Verification(
			params.get("vnp_TxnRef"),
			params.get("vnp_TransactionNo"),
			SUCCESS_CODE.equals(params.get("vnp_ResponseCode")),
			params.get("vnp_ResponseCode"),
			params.get("vnp_TransactionStatus"));
	}

	/** A callback whose signature has been checked. */
	public record Verification(
			String txnRef,
			String transactionNo,
			boolean success,
			String responseCode,
			String transactionStatus) {
	}	/** Sorted, URL-encoded query string built only from the signed parameters. */
	public static String canonicalQuery(Map<String, String> params) {
		return new TreeMap<>(params).entrySet().stream()
			.filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
			.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
			.collect(Collectors.joining("&"));
	}

	public static String hmacSha512(String secret, String data) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA512);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not compute the VNPay signature", ex);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
