# Ableron Java Library
[![Build Status](https://github.com/ableron/ableron-java/actions/workflows/main.yml/badge.svg)](https://github.com/ableron/ableron-java/actions/workflows/main.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.ableron/ableron/badge.svg)](https://mvnrepository.com/artifact/io.github.ableron/ableron)
[![Java Version](https://img.shields.io/badge/Java-11+-4EB1BA.svg)](https://docs.oracle.com/en/java/javase/11/)

Java Library for Ableron Server Side UI Composition

## Installation
Gradle:
```groovy
implementation 'io.github.ableron:ableron:1.9.0'
```

Maven:
```xml
<dependency>
  <groupId>io.github.ableron</groupId>
  <artifactId>ableron</artifactId>
  <version>1.9.0</version>
</dependency>
```

## Usage
1. Init ableron
   ```java
   var ableron = new Ableron(AbleronConfig.builder()
     .cacheMaxSizeInBytes(1024 * 1024 * 50)
     .build());
   ```
2. Use includes in response body
   ```html
   <html>
     <head>
       <ableron-include src="https://head-fragment" />
     </head>
     <body>
       <ableron-include src="https://body-fragment" fallback-src="https://fallback-body-fragment"><!-- Static fallback fragment goes here --></ableron-include>
     </body>
   </html>
   ```
3. Apply transclusion to response if applicable (HTTP status 2xx, 4xx or 5xx; Response content type is non-binary, ...)
   ```java
   // perform transclusion based on unprocessed response body and request headers from e.g. HttpServletRequest
   TransclusionResult transclusionResult = ableron.resolveIncludes(getOriginalResponseBody(), getRequestHeaders());
   // set body to the processed one
   setResponseBody(transclusionResult.getContent());
   // override response status code when primary include was present
   transclusionResult.getStatusCodeOverride().ifPresent(statusCode -> setResponseStatusCode(statusCode));
   // add response headers when primary include was present
   addResponseHeaders(transclusionResult.getResponseHeadersToPass());
   // set cache-control header
   getResponse().setHeader(CACHE_CONTROL, transclusionResult.calculateCacheControlHeaderValue(getResponseHeaders()));
   ```

### Configuration

#### `enabled`

Default: `true`

Whether UI composition is enabled.

#### `fragmentRequestTimeout`

Default: `3 seconds`

Timeout for requesting fragments.

#### `fragmentRequestHeadersToPass`

Default:

```java
List.of(
    "Accept-Language",
    "Correlation-ID",
    "Forwarded",
    "Referer",
    "User-Agent",
    "X-Correlation-ID",
    "X-Forwarded-For",
    "X-Forwarded-Proto",
    "X-Forwarded-Host",
    "X-Real-IP",
    "X-Request-ID"
);
```

Request headers that are passed to fragment requests, if present.

#### `fragmentAdditionalRequestHeadersToPass`

Default: `[]`

Extends `fragmentRequestHeadersToPass`. Use this property to pass all headers defined in `fragmentRequestHeadersToPass`
plus the additional headers defined here. This prevents the need to duplicate `fragmentRequestHeadersToPass` if the only
use case is to add additional headers instead of modifying the default ones.

#### `primaryFragmentResponseHeadersToPass`

```java
List.of(
    "Content-Language",
    "Location",
    "Refresh"
);
```

Response headers of primary fragments to pass to the page response, if present.

#### `cacheMaxSizeInBytes`

Default: `10 MB`

Maximum size in bytes the fragment cache may have.

#### `cacheVaryByRequestHeaders`

Default: `empty list`

Fragment request headers which influence the requested fragment aside from its URL. Used to create fragment cache keys.
Must be a subset of `fragmentRequestHeadersToPass`. Common example are headers used for steering A/B-tests.

#### `statsAppendToContent`

Default: `false`

Whether to append UI composition stats as HTML comment to the content.

#### `statsExposeFragmentUrl`

Default: `false`

Whether to expose fragment URLs in the stats appended to the content.

## Contributing

All contributions are greatly appreciated, be it pull requests, feature requests or bug reports. See
[ableron.github.io](https://ableron.github.io/) for details.

## License

Licensed under [MIT](./LICENSE).
