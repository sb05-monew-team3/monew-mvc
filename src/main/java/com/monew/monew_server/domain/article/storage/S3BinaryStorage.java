package com.monew.monew_server.domain.article.storage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monew.monew_server.domain.article.dto.ArticleSaveDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3BinaryStorage {

	private final S3Client s3Client;

	@Value("${spring.aws.s3.bucket}")
	private String bucketName;

	public List<ArticleSaveDto> getBackupArticles(String interest, LocalDateTime date) {
		String dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		String prefix = String.format("article/%s/%s/", interest, dateString);

		log.debug("S3 백업 파일 목록 요청: {}/{}", bucketName, prefix);

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		List<ArticleSaveDto> result = new ArrayList<>();
		String continuationToken = null;

		do {
			ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
				.bucket(bucketName)
				.prefix(prefix);

			if (continuationToken != null) {
				reqBuilder.continuationToken(continuationToken);
			}

			ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(reqBuilder.build());

			log.info("S3 목록 응답 - prefix: {}, 이번 페이지 개수: {}, isTruncated: {}",
				prefix, listObjectsV2Response.keyCount(), listObjectsV2Response.isTruncated());

			for (S3Object obj : listObjectsV2Response.contents()) {
				if (obj.key().endsWith("/")) {
					continue;
				}

				log.debug("-> UUID 파일 로드: {}", obj.key());
				GetObjectRequest getRequest = GetObjectRequest.builder()
					.bucket(bucketName)
					.key(obj.key())
					.build();

				try (ResponseInputStream<?> stream = s3Client.getObject(getRequest)) {
					ArticleSaveDto dto = objectMapper.readValue(stream, ArticleSaveDto.class);
					if (dto != null) {
						result.add(dto);
					}
				} catch (NoSuchKeyException e) {
					log.warn("파일 로드 실패: {}", obj.key());
				} catch (Exception e) {
					log.error("파일 파싱 실패: {}", obj.key(), e);
				}
			}

			continuationToken = listObjectsV2Response.nextContinuationToken();
		} while (continuationToken != null);

		if (result.isEmpty()) {
			log.warn("백업 파일을 찾을 수 없음: {}", prefix);
		} else {
			log.info("총 {}개 기사 백업 로드 완료 - {}", result.size(), prefix);
		}

		return result;
	}
}
