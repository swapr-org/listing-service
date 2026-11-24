package app.swapr.listing.photos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
class PhotoStorageConfig {

  @Bean
  S3Client s3Client(PhotoProperties properties) {
    return S3Client.builder()
        .region(Region.of(properties.region()))
        .credentialsProvider(() -> AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build();
  }

  @Bean
  PhotoBucketHealthIndicator photoBucketHealthIndicator(S3Client s3, PhotoProperties properties) {
    return new PhotoBucketHealthIndicator(s3, properties.bucket());
  }
}
