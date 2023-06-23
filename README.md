# Ableron Java Library
[![Build Status](https://github.com/ableron/ableron-java/actions/workflows/main.yml/badge.svg)](https://github.com/ableron/ableron-java/actions/workflows/main.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.ableron/ableron/badge.svg)](https://mvnrepository.com/artifact/io.github.ableron/ableron)
[![Java Version](https://img.shields.io/badge/Java-11+-4EB1BA.svg)](https://docs.oracle.com/en/java/javase/11/)

Java Library for Ableron Server Side UI Composition

## Installation
Add dependency [io.github.ableron:ableron](https://mvnrepository.com/artifact/io.github.ableron/ableron) to your project.

Gradle:
```groovy
implementation 'io.github.ableron:ableron:1.2.0'
```

Maven:
```xml
<dependency>
  <groupId>io.github.ableron</groupId>
  <artifactId>ableron</artifactId>
  <version>1.2.0</version>
</dependency>
```

## Usage
1. Init ableron
   ```java
   var ableron = new Ableron(AbleronConfig.builder()
     .cacheMaxSizeInBytes(1024 * 1024 * 50)
     .build());
   ```
1. Use includes in response body
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
1. Apply transclusion to response if applicable (HTTP status 2xx, 4xx or 5xx; Response content type is non-binary, ...)
   ```java
   // get request headers from e.g. HttpServletRequest
   Map<String, List<String>> presentRequestHeaders = getRequestHeaders();
   // get unprocessed response body
   String originalResponseBody = getOriginalResponseBody();
   // perform transclusion
   TransclusionResult transclusionResult = ableron.resolveIncludes(originalResponseBody, presentRequestHeaders);
   // set body to the processed one
   setResponseBody(transclusionResult.getContent());
   // check whether there was a primary include
   if (transclusionResult.hasPrimaryInclude()) {
     // override response status code when primary include was present
     transclusionResult.getPrimaryIncludeStatusCode().ifPresent(statusCode -> setResponseStatusCode(statusCode));
     // add response headers when primary include was present
     addResponseHeaders(transclusionResult.getPrimaryIncludeResponseHeaders());
   }
   // reduce response cache time according to the lowest fragment cache time
   transclusionResult.getContentExpirationTime().ifPresent(contentExpirationTime -> {
     // check whether response may be cached
     if (contentExpirationTime.isAfter(Instant.now())) {
       // override response expiration time
       removeResponseHeader("Age");
       setResponseHeader("Cache-Control", "max-age=0");
     } else {
       // make sure, response will not be cached if fragment expiration time is set to past
       setResponseHeader("Cache-Control", "no-store, no-cache, max-age=0");
     }
   });
   ```

### Configuration Options
* `enabled`: Whether UI composition is enabled. Defaults to `true`
* `fragmentRequestTimeout`: Timeout for requesting fragments. Defaults to `3 seconds`
* `fragmentDefaultCacheDuration`: Duration to cache fragments in case neither `Cache-Control` nor `Expires` header is present. Defaults to `5 minutes`
* `fragmentRequestHeadersToPass`: Request headers that are passed to fragment requests if present. Defaults to:
  * `Accept-Language`
  * `Correlation-ID`
  * `Forwarded`
  * `Referer`
  * `User-Agent`
  * `X-Correlation-ID`
  * `X-Forwarded-For`
  * `X-Forwarded-Proto`
  * `X-Forwarded-Host`
  * `X-Real-IP`
  * `X-Request-ID`
* `primaryFragmentResponseHeadersToPass`: Response headers of primary fragments to pass to the page response if present. Defaults to:
  * `Content-Language`
  * `Location`
  * `Refresh`
* `cacheMaxSizeInBytes`: Maximum size in bytes the fragment cache may have. Defaults to `10 MB`

### Include Tag
* Must be closed, i.e. either `<ableron-include ... />` or `<ableron-include ...></ableron-include>`
* Content between `<ableron-include>` and `</ableron-include>` is used as fallback content
* Attributes
  * `src`: URL of the fragment to include
  * `src-timeout-millis`: Timeout for requesting the `src` URL. Defaults to global `requestTimeout`
  * `fallback-src`: URL of the fragment to include in case the request to `src` failed
  * `fallback-src-timeout-millis`: Timeout for requesting the `fallback-src` URL. Defaults to global `requestTimeout`
  * `primary`: Denotes a fragment whose response code is set as response code for the page
    * If `src` returns success status, this status code is set as response code for the page
    * If `src` returns error status, `fallback-src` is defined and returns success status, this status code is set as response code for the page
    * If `src` and `fallback-src` return error status, the status code returned by `src` is set as response code for the page
    * If `src` and `fallback-src` return error status, the fragment content equals the body returned by `src`. Fallback content is ignored
* Precedence for resolving: `src` → `fallback-src` → fallback content

### Redirects
Redirects will not be followed when requesting fragments because they may introduce unwanted latency.

### Caching
Fragments are considered cacheable if they have HTTP status code
   * `200`, `203`, `204`, `206`,
   * `300`,
   * `404`, `405`, `410`, `414`,
   * `501`

### Cache-Control
The transclusion result provides a max-age for the content with all includes resolved,
based on the fragment with the lowest expiration time.
I.e. the fragment with the lowest expiration time defines the max-age of the page.

## Contributing
Contributions are greatly appreciated. To contribute you can either simply open an issue or fork the repository and create a pull request:
1. Fork this repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Added some amazing feature'`)
4. Push to your branch (`git push origin feature/amazing-feature`)
5. Open a pull request

## Library Development
See [DEVELOPMENT.md](./DEVELOPMENT.md) for details regarding development of this library.
