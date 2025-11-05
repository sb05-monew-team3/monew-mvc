package com.monew.monew_server.log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "logging.s3.enabled", havingValue = "true")
public class S3LogUploader {

	private final S3Client s3Client;
	private final LoggingS3Properties properties;

	public void uploadLogFile(LocalDate date) {
		String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		String logFileName = "application." + dateStr + ".log";
		Path logFilePath = Paths.get(properties.getLogPath(), logFileName);

		if (!Files.exists(logFilePath)) {
			log.warn("로그 파일이 존재하지 않습니다: {}", logFilePath);
			return;
		}

		try {
			File logFile = logFilePath.toFile();

			String s3Key = properties.getPrefix() + date.getYear() + "/"
				+ String.format("%02d", date.getMonthValue()) + "/"
				+ logFileName;

			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(properties.getBucketName())
				.key(s3Key)
				.contentType("text/plain")
				.build();

			s3Client.putObject(putObjectRequest, RequestBody.fromFile(logFile));

			log.info("로그 파일 S3 업로드 완료: {} -> s3://{}/{}",
				logFilePath, properties.getBucketName(), s3Key);

		} catch (Exception e) {
			log.error("로그 파일 S3 업로드 실패: {}", logFilePath, e);
		}
	}

	public void uploadLogFiles(LocalDate startDate, LocalDate endDate) {
		LocalDate current = startDate;
		int successCount = 0;
		int failCount = 0;

		while (!current.isAfter(endDate)) {
			try {
				uploadLogFile(current);
				successCount++;
			} catch (Exception e) {
				log.error("날짜 {} 로그 업로드 실패", current, e);
				failCount++;
			}
			current = current.plusDays(1);
		}

		log.info("로그 일괄 업로드 완료 - 성공: {}, 실패: {}", successCount, failCount);
	}

	public void uploadAllOldLogs(int daysAgo) {
		LocalDate cutoffDate = LocalDate.now().minusDays(daysAgo);

		try (Stream<Path> paths = Files.list(Paths.get(properties.getLogPath()))) {
			paths.filter(Files::isRegularFile)
				.filter(path -> path.toString().endsWith(".log"))
				.filter(path -> !path.toString().contains("application.log"))
				.filter(path -> !path.toString().contains("error.log"))
				.filter(path -> isOlderThan(path, cutoffDate))
				.forEach(path -> {
					LocalDate logDate = extractDateFromFileName(path.getFileName().toString());
					if (logDate != null) {
						uploadLogFile(logDate);
					}
				});
		} catch (IOException e) {
			log.error("로그 파일 목록 조회 실패", e);
		}
	}

	private boolean isOlderThan(Path path, LocalDate cutoffDate) {
		try {
			LocalDate fileDate = LocalDate.ofInstant(
				Files.getLastModifiedTime(path).toInstant(),
				java.time.ZoneId.systemDefault()
			);
			return fileDate.isBefore(cutoffDate);
		} catch (IOException e) {
			log.warn("파일 날짜 확인 실패: {}", path, e);
			return false;
		}
	}

	private LocalDate extractDateFromFileName(String fileName) {
		try {
			String dateStr = fileName.replaceAll("(application\\.|error\\.)", "").replace(".log", "");
			return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		} catch (Exception e) {
			log.warn("파일명에서 날짜 추출 실패: {}", fileName);
			return null;
		}
	}
}