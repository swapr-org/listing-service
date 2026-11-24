package app.swapr.listing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("swapr.photos")
record PhotoProperties(String bucket, String region, String accessKeyId, String secretAccessKey) {

  PhotoProperties {
    requireSet(bucket, "PHOTO_BUCKET");
    requireSet(region, "AWS_REGION");
  }

  private static void requireSet(String value, String variable) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(variable + " must be set to a non-blank value");
    }
  }
}
