package com.monew.monew_server.exception;

import java.util.UUID;

public class NotificationNotFoundException extends BaseException {

	public NotificationNotFoundException() {
		super(ErrorCode.NOTIFICATION_NOT_FOUND);
	}

	public static NotificationNotFoundException withId(UUID notificationId) {
		NotificationNotFoundException exception = new NotificationNotFoundException();
		exception.addDetail("notificationId", notificationId);
		return exception;
	}
} 