package com.monew.monew_server.domain.article.storage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monew.monew_server.domain.article.dto.ArticleSaveDto;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3BinaryStorage {

	private final S3Client s3Client;

	@Value("${spring.app.aws.s3.bucket-name}")
	private String bucketName;

	public List<ArticleSaveDto> getBackupArticles(LocalDateTime date) {
		String dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		String prefix = String.format("article/%s/", dateString);

		log.info("S3 백업 파일 목록 요청: {}/{}", bucketName, prefix);

		ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
			.bucket(bucketName)
			.prefix(prefix)
			.build();

		List<S3Object> objects = s3Client.listObjectsV2(listRequest).contents();

		if (objects.isEmpty()) {
			log.warn("백업 파일을 찾을 수 없음: {}", prefix);
			return List.of();
		}

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		return objects.stream()
			.map(S3Object::key)
			.filter(key -> !key.endsWith("/"))
			.map(key -> {
				log.info("-> UUID 파일 로드: {}", key);
				GetObjectRequest getRequest = GetObjectRequest.builder()
					.bucket(bucketName)
					.key(key)
					.build();

				try (ResponseInputStream<?> stream = s3Client.getObject(getRequest)) {
					return objectMapper.readValue(stream, ArticleSaveDto.class);
				} catch (NoSuchKeyException e) {
					log.warn("파일 로드 실패: {}", key);
					return null;
				} catch (Exception e) {
					log.error("파일 파싱 실패: {}", key, e);
					return null;
				}
			})
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}
}
