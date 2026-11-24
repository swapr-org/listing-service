package app.swapr.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@SpringBootTest(properties = {
    ListingServiceApplicationTest.NO_DATABASE,
    "INTERNAL_TOKEN=test-internal-token",
    "SUPABASE_URL=https://test-project.supabase.co",
    "CORS_ALLOWED_ORIGINS=https://qa.swapr.test",
    "PHOTO_BUCKET=swapr-test-photos",
    "AWS_REGION=eu-west-2"
})
@ActiveProfiles("qa")
class ListingServiceApplicationTest {

  static final String NO_DATABASE =
      "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration";

  @MockitoBean
  private S3Client s3;

  @Autowired
  private HealthEndpoint health;

  @Test
  void reportsHealthyWhenPhotoBucketIsUp() {
    HealthDescriptor result = health.health();

    assertThat(result.getStatus()).isEqualTo(Status.UP);
    assertThat(((CompositeHealthDescriptor) result).getComponents())
        .extractingByKey("photoBucket")
        .extracting(HealthDescriptor::getStatus)
        .isEqualTo(Status.UP);
  }

  @ParameterizedTest
  @MethodSource("bucketFailures")
  void reportsDownWhenPhotoBucketCannotBeReached(SdkException failure) {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(failure);

    HealthDescriptor result = health.health();

    assertThat(result.getStatus()).isEqualTo(Status.DOWN);
    assertThat(((CompositeHealthDescriptor) result).getComponents())
        .extractingByKey("photoBucket")
        .extracting(HealthDescriptor::getStatus)
        .isEqualTo(Status.DOWN);
  }

  @Test
  void livenessIgnoresPhotoBucket() {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(SdkClientException.create("unreachable"));

    HealthDescriptor liveness = health.healthForPath("liveness");

    assertThat(liveness.getStatus()).isEqualTo(Status.UP);
    assertThat(((CompositeHealthDescriptor) liveness).getComponents()).doesNotContainKey("photoBucket");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PHOTO_BUCKET", "AWS_REGION"})
  void failsToStartWhenPhotoBucketSettingIsEmpty(String emptySetting) {
    SpringApplicationBuilder app = new SpringApplicationBuilder(ListingServiceApplication.class)
        .profiles("qa")
        .properties(
            NO_DATABASE,
            "server.port=0",
            "INTERNAL_TOKEN=test-internal-token",
            "SUPABASE_URL=https://test-project.supabase.co",
            "PHOTO_BUCKET=swapr-test-photos",
            "AWS_REGION=eu-west-2",
            emptySetting + "=");

    assertThatThrownBy(app::run).rootCause().hasMessageContaining(emptySetting);
  }

  static Stream<SdkException> bucketFailures() {
    return Stream.of(
        NoSuchBucketException.builder().statusCode(404).message("Not Found").build(),
        S3Exception.builder().statusCode(403).message("Forbidden").build(),
        SdkClientException.create("Unable to execute HTTP request"));
  }
}
