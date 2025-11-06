package com.monew.monew_server.log;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "logging.s3")
public class LoggingS3Properties {

	private boolean enabled = false;

	private String bucketName;

	private String prefix = "logs/";

	private String logPath = ".logs";
}