package com.monew.monew_server.config;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MDCLoggingInterceptor implements HandlerInterceptor {

	public static final String REQUEST_ID = "requestId";
	public static final String REQUEST_URI = "requestUri";  // requestUrl → requestUri로 변경
	public static final String REQUEST_METHOD = "requestMethod";
	public static final String IP_ADDRESS = "ipAddress";
	public static final String REQUEST_ID_HEADER = "Monew-Request-Id";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws
		Exception {

		String clientIp = getClientIp(request);

		String requestId = clientIp + "-" + UUID.randomUUID().toString().substring(0, 8);

		MDC.put(REQUEST_ID, requestId);
		MDC.put(REQUEST_URI, request.getRequestURI());
		MDC.put(REQUEST_METHOD, request.getMethod());
		MDC.put(IP_ADDRESS, clientIp);

		response.setHeader(REQUEST_ID_HEADER, requestId);

		log.debug("Request Start");

		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
		Exception ex) throws Exception {
		log.debug("Request End");
		MDC.clear();
	}

	private String getClientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
			ip = ip.split(",")[0].trim();
		} else {
			ip = request.getRemoteAddr();
		}

		try {
			InetAddress inetAddress = InetAddress.getByName(ip);
			if (inetAddress.isLoopbackAddress()) {
				return "127.0.0.1";
			}
			if (inetAddress instanceof Inet6Address && ip.startsWith("::ffff:")) {
				return ip.substring(7);
			}
			return inetAddress.getHostAddress();
		} catch (UnknownHostException e) {
			return ip;
		}
	}

}
