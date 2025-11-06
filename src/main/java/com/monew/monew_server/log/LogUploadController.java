package com.monew.monew_server.log;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@ConditionalOnBean(S3LogUploader.class)
public class LogUploadController {

	private final S3LogUploader s3LogUploader;

	@PostMapping("/upload")
	public ResponseEntity<String> uploadLog(
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		log.info("로그 수동 업로드 요청: {}", date);

		s3LogUploader.uploadLogFile(date);

		return ResponseEntity.ok("로그 업로드 완료: " + date);
	}

	@PostMapping("/upload/range")
	public ResponseEntity<String> uploadLogRange(
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
	) {
		log.info("로그 범위 업로드 요청: {} ~ {}", startDate, endDate);

		s3LogUploader.uploadLogFiles(startDate, endDate);

		return ResponseEntity.ok("로그 범위 업로드 완료: " + startDate + " ~ " + endDate);
	}

	@PostMapping("/upload/old")
	public ResponseEntity<String> uploadOldLogs(
		@RequestParam(defaultValue = "7") int daysAgo
	) {
		log.info("오래된 로그 일괄 업로드 요청: {}일 이전", daysAgo);

		s3LogUploader.uploadAllOldLogs(daysAgo);

		return ResponseEntity.ok("오래된 로그 업로드 완료: " + daysAgo + "일 이전");
	}
}
