package com.monew.monew_server.log;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(S3LogUploader.class)
public class LogUploadScheduler {

	private final S3LogUploader s3LogUploader;

	@Scheduled(cron = "0 0 2 * * *")
	public void uploadYesterdayLog() {
		log.info("전날 로그 S3 업로드 작업 시작");

		try {
			LocalDate yesterday = LocalDate.now().minusDays(1);
			s3LogUploader.uploadLogFile(yesterday);

			log.info("전날 로그 S3 업로드 작업 완료");
		} catch (Exception e) {
			log.error("전날 로그 S3 업로드 작업 실패", e);
		}
	}

	@Scheduled(cron = "0 0 3 * * SUN")
	public void uploadOldLogs() {
		log.info("오래된 로그 일괄 업로드 작업 시작");

		try {
			s3LogUploader.uploadAllOldLogs(7);

			log.info("오래된 로그 일괄 업로드 작업 완료");
		} catch (Exception e) {
			log.error("오래된 로그 일괄 업로드 작업 실패", e);
		}
	}
}