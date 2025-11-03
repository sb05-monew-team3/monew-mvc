package com.monew.monew_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("local")
public class S3Config {

	@Value("${spring.aws.accessKeyId}")
	private String accessKeyId;

	@Value("${spring.aws.secretKey}")
	private String secretKey;

	@Value("${spring.aws.region}")
	private String region;

	@Bean
	public S3Client s3Client() {
		AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretKey);

		return S3Client.builder()
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
			.build();
	}
}
