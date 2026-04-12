package com.crowdlens.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and encodes product images for the verdict card.
 *
 * Responsibilities:
 *  1. Amazon scrape fallback — fetches the first product image from Amazon
 *     search results when Reddit yields no validated image.
 *  2. Base64 encoding — downloads the final image and encodes it as a
 *     data URI so html-to-image can embed it reliably in the share card PNG
 *     without cross-origin issues.
 */
@Slf4j
@Service
public class ImageResolutionService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);
    private static final int MAX_IMAGE_BYTES = 512 * 1024; // 512 KB — enough for thumbnails

    // Matches the main product image on Amazon search results pages
    private static final Pattern AMAZON_IMG_PATTERN =
            Pattern.compile("class=\"s-image\"[^>]*src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Attempts to find a product image on Amazon India (fallback to Amazon US).
     *
     * @param query Product search query
     * @return Optional image URL, or empty if none found
     */
    public Optional<String> fetchFromAmazon(String query) {
        // Try Amazon India first, fallback to Amazon US
        String[] amazonUrls = {
            "https://www.amazon.in/s?k=" + encodeQuery(query),
            "https://www.amazon.com/s?k=" + encodeQuery(query),
        };

        for (String searchUrl : amazonUrls) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .timeout(HTTP_TIMEOUT)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.debug("Amazon search returned {} for query '{}' on {}", response.statusCode(), query, searchUrl);
                    continue;
                }

                Matcher matcher = AMAZON_IMG_PATTERN.matcher(response.body());
                if (matcher.find()) {
                    String imageUrl = matcher.group(1);
                    log.info("Amazon image found for query '{}': {}", query, imageUrl);
                    return Optional.of(imageUrl);
                }

                log.debug("No s-image found on Amazon search page for query '{}'", query);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Amazon image fetch interrupted for query '{}'", query);
                return Optional.empty();
            } catch (Exception e) {
                log.debug("Amazon image fetch failed for '{}' on {}: {}", query, searchUrl, e.getMessage());
            }
        }

        return Optional.empty();
    }

    /**
     * Downloads the image at {@code imageUrl} and returns it as a base64 data URI
     * (e.g. "data:image/jpeg;base64,...").
     *
     * Used to embed the product image directly into the DynamoDB-cached response so that
     * html-to-image can render it in the share card PNG without cross-origin restrictions.
     *
     * @param imageUrl The resolved image URL to download
     * @return Optional base64 data URI, or empty if download fails / image is too large
     */
    public Optional<String> toBase64DataUri(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return Optional.empty();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(HTTP_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.debug("Image download returned {} for URL: {}", response.statusCode(), imageUrl);
                return Optional.empty();
            }

            byte[] imageBytes = response.body();
            if (imageBytes.length > MAX_IMAGE_BYTES) {
                log.debug("Image too large ({} bytes) — skipping base64 encoding for URL: {}", imageBytes.length, imageUrl);
                return Optional.empty();
            }

            // Detect MIME type from Content-Type header or URL extension
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse(inferMimeType(imageUrl));

            // Strip parameters (e.g. "image/jpeg; charset=utf-8" → "image/jpeg")
            if (contentType.contains(";")) {
                contentType = contentType.substring(0, contentType.indexOf(';')).trim();
            }

            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String dataUri = "data:" + contentType + ";base64," + base64;

            log.info("Image encoded to base64 ({} bytes → {} chars data URI)", imageBytes.length, dataUri.length());
            return Optional.of(dataUri);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Image base64 encoding interrupted for URL: {}", imageUrl);
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Image base64 encoding failed for URL '{}': {}", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private String inferMimeType(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg"; // Safe default for Amazon and Reddit CDN images
    }

    private String encodeQuery(String query) {
        return query.trim().replace(" ", "+");
    }
}
