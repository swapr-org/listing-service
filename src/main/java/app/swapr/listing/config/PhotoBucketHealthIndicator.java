package app.swapr.listing.config;

import java.time.Duration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

class PhotoBucketHealthIndicator implements HealthIndicator {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final S3Client s3;
  private final HeadBucketRequest request;

  PhotoBucketHealthIndicator(S3Client s3, String bucket) {
    this.s3 = s3;
    this.request = HeadBucketRequest.builder()
        .bucket(bucket)
        .overrideConfiguration(o -> o.apiCallAttemptTimeout(TIMEOUT).apiCallTimeout(TIMEOUT))
        .build();
  }

  @Override
  public Health health() {
    try {
      s3.headBucket(request);
      return Health.up().build();
    } catch (RuntimeException e) {
      return Health.down(e).build();
    }
  }
}
