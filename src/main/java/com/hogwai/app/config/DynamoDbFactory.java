package com.hogwai.app.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Factory
public class DynamoDbFactory {

    @Singleton
    @Primary
    DynamoDbClient dynamoDbClient(
            @Value("${aws.region:eu-west-3}") String region,
            @Value("${aws.dynamodb.endpoint-override:}") String endpoint,
            @Value("${aws.credentials.static.access-key-id:fake}") String accessKey,
            @Value("${aws.credentials.static.secret-access-key:fake}") String secretKey
    ) {
        return DynamoDbClient.builder()
                             .region(Region.of(region))
                             .endpointOverride(endpoint != null && !endpoint.isBlank() ? URI.create(endpoint) : null)
                             .credentialsProvider(
                                     StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                             )
                             .build();
    }

    @Singleton
    DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient existingClient) {
        return DynamoDbEnhancedClient.builder()
                                     .dynamoDbClient(existingClient)
                                     .build();
    }
}


