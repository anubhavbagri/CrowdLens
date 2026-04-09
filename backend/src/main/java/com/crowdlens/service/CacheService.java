package com.crowdlens.service;

import com.crowdlens.config.DynamoDbProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * DynamoDB-backed cache service.
 * Stores serialized search responses with TTL auto-expiry.
 * Key: SHA-256 hash of normalized query → Value: serialized JSON response.
 */
@Slf4j
@Service
public class CacheService {

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbProperties props;
    private final ObjectMapper objectMapper;

    public CacheService(DynamoDbClient dynamoDbClient, DynamoDbProperties props, ObjectMapper objectMapper) {
        this.dynamoDbClient = dynamoDbClient;
        this.props = props;
        this.objectMapper = objectMapper;
        ensureTableExists();
    }

    /**
     * Retrieves a cached response for the given query.
     *
     * @param queryNormalized Normalized search query
     * @return Cached JSON response if present and not expired
     */
    public Optional<String> get(String queryNormalized) {
        String queryHash = hashQuery(queryNormalized);

        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(props.tableName())
                    .key(Map.of("query_hash", AttributeValue.builder().s(queryHash).build()))
                    .build());

            if (!response.hasItem() || response.item().isEmpty()) {
                log.debug("Cache MISS for query: '{}'", queryNormalized);
                return Optional.empty();
            }

            Map<String, AttributeValue> item = response.item();

            // Check TTL manually (DynamoDB TTL deletion is eventual, not immediate)
            if (item.containsKey("expires_at")) {
                long expiresAt = Long.parseLong(item.get("expires_at").n());
                if (Instant.now().getEpochSecond() > expiresAt) {
                    log.debug("Cache EXPIRED for query: '{}'", queryNormalized);
                    return Optional.empty();
                }
            }

            String cachedJson = item.get("response_json").s();
            log.info("Cache HIT for query: '{}'", queryNormalized);
            return Optional.of(cachedJson);

        } catch (Exception e) {
            log.warn("Cache GET failed for query '{}': {}", queryNormalized, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Scans cache for an entry with Jaccard word-set similarity >= similarityThreshold.
     * Called as fallback when exact hash lookup misses.
     *
     * @param queryNormalized Normalized search query
     * @return Cached JSON response of the most similar matching entry, if any
     */
    public Optional<String> findSimilar(String queryNormalized) {
        Set<String> queryWords = new HashSet<>(Arrays.asList(queryNormalized.split(" ")));

        try {
            ScanResponse scanResponse = dynamoDbClient.scan(ScanRequest.builder()
                    .tableName(props.tableName())
                    .filterExpression("expires_at > :now")
                    .expressionAttributeValues(Map.of(
                            ":now", AttributeValue.builder()
                                    .n(String.valueOf(Instant.now().getEpochSecond()))
                                    .build()))
                    .projectionExpression("query_normalized, response_json")
                    .build());

            String bestJson = null;
            double bestScore = props.similarityThreshold();

            for (Map<String, AttributeValue> item : scanResponse.items()) {
                if (!item.containsKey("query_normalized") || !item.containsKey("response_json")) continue;

                Set<String> cachedWords = new HashSet<>(Arrays.asList(item.get("query_normalized").s().split(" ")));
                double score = jaccardSimilarity(queryWords, cachedWords);
                if (score > bestScore) {
                    bestScore = score;
                    bestJson = item.get("response_json").s();
                }
            }

            if (bestJson != null) {
                log.info("Cache SIMILAR HIT (score={}) for query: '{}'", String.format("%.2f", bestScore), queryNormalized);
                return Optional.of(bestJson);
            }
            log.debug("Cache SIMILAR MISS for query: '{}'", queryNormalized);

        } catch (Exception e) {
            log.warn("Cache SCAN failed for similarity lookup on query '{}': {}", queryNormalized, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Stores a response in the cache with TTL.
     *
     * @param queryNormalized Normalized query
     * @param responseJson    Serialized response JSON
     */
    public void put(String queryNormalized, String responseJson) {
        String queryHash = hashQuery(queryNormalized);
        long expiresAt = Instant.now().plusSeconds((long) props.ttlHours() * 3600).getEpochSecond();

        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(props.tableName())
                    .item(Map.of(
                            "query_hash", AttributeValue.builder().s(queryHash).build(),
                            "query_normalized", AttributeValue.builder().s(queryNormalized).build(),
                            "response_json", AttributeValue.builder().s(responseJson).build(),
                            "expires_at", AttributeValue.builder().n(String.valueOf(expiresAt)).build(),
                            "created_at", AttributeValue.builder().s(Instant.now().toString()).build()))
                    .build());

            log.info("Cache PUT for query: '{}' (TTL: {}h)", queryNormalized, props.ttlHours());
        } catch (Exception e) {
            log.warn("Cache PUT failed for query '{}': {}", queryNormalized, e.getMessage());
        }
    }

    /**
     * Evicts a cached entry.
     */
    public void evict(String queryNormalized) {
        String queryHash = hashQuery(queryNormalized);
        try {
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(props.tableName())
                    .key(Map.of("query_hash", AttributeValue.builder().s(queryHash).build()))
                    .build());
            log.info("Cache EVICT for query: '{}'", queryNormalized);
        } catch (Exception e) {
            log.warn("Cache EVICT failed for query '{}': {}", queryNormalized, e.getMessage());
        }
    }

    /**
     * Serializes an object to JSON.
     */
    public String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Deserializes a JSON string to the given type.
     */
    public <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    private String hashQuery(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Creates the DynamoDB table if it doesn't exist.
     * Degrades gracefully — app starts even if DynamoDB is unreachable.
     */
    private void ensureTableExists() {
        try {
            dynamoDbClient.describeTable(DescribeTableRequest.builder()
                    .tableName(props.tableName())
                    .build());
            log.info("DynamoDB table '{}' exists", props.tableName());
        } catch (ResourceNotFoundException e) {
            log.info("Creating DynamoDB table '{}'...", props.tableName());
            try {
                dynamoDbClient.createTable(CreateTableRequest.builder()
                        .tableName(props.tableName())
                        .keySchema(KeySchemaElement.builder()
                                .attributeName("query_hash")
                                .keyType(KeyType.HASH)
                                .build())
                        .attributeDefinitions(AttributeDefinition.builder()
                                .attributeName("query_hash")
                                .attributeType(ScalarAttributeType.S)
                                .build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .build());

                // Enable TTL
                dynamoDbClient.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                        .tableName(props.tableName())
                        .timeToLiveSpecification(TimeToLiveSpecification.builder()
                                .attributeName("expires_at")
                                .enabled(true)
                                .build())
                        .build());

                log.info("DynamoDB table '{}' created with TTL on 'expires_at'", props.tableName());
            } catch (Exception ex) {
                log.warn("Failed to create DynamoDB table: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("DynamoDB unavailable at startup (cache will be skipped): {}", e.getMessage());
        }
    }
}
