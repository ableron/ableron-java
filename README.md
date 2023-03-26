# Ableron Java Library
[![Build Status](https://github.com/ableron/ableron-java/actions/workflows/main.yml/badge.svg)](https://github.com/ableron/ableron-java/actions/workflows/main.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.ableron/ableron/badge.svg)](https://mvnrepository.com/artifact/io.github.ableron/ableron)
[![Java Version](https://img.shields.io/badge/Java-11+-4EB1BA.svg)](https://docs.oracle.com/en/java/javase/11/)

Java Library for Ableron Server Side UI Composition

## Installation
Maven:
```xml
<dependency>
  <groupId>io.github.ableron</groupId>
  <artifactId>ableron</artifactId>
  <version>1.0.2</version>
</dependency>
```
Gradle:
```groovy
dependencies {
  implementation 'io.github.ableron:ableron:1.0.2'
}
```

## Usage
1. Init ableron
   ```java
   var ableronConfig = AbleronConfig.builder().build();
   var ableron = new Ableron(ableronConfig);
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
   TransclusionResult transclusionResult = ableron.resolveIncludes(originalResponseBody);
   String processedResponseBody = transclusionResult.getContent();
   ```

### Configuration Options
* `enabled`: Whether UI composition is enabled. Defaults to `true`
* `requestTimeout`: Timeout for requesting fragments. Defaults to `3 seconds`
* `defaultFragmentCacheDuration`: Duration to cache fragments in case neither `Cache-Control` nor `Expires` header is present. Defaults to `5 minutes`
* `maxCacheSizeInBytes`: Maximum size in bytes the fragment cache may have. Defaults to `10 MB`
* `requestHeadersToPass`: Request headers that are passed to fragment requests if present. Defaults to
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

### Include Tag
* Must be closed, i.e. either `<ableron-include ... />` or `<ableron-include ...></ableron-include>`
* Content between `<ableron-include>` and `</ableron-include>` is used as fallback content
* Attributes
   * `src`: URL of the fragment to include
   * `src-timeout-millis`: Timeout for requesting the `src` URL. Defaults to global `requestTimeout`
   * `fallback-src`: URL of the fragment to include in case the request to `src` failed
   * `fallback-src-timeout-millis`: Timeout for requesting the `fallback-src` URL. Defaults to global `requestTimeout`
* Precedence for resolving: `src` → `fallback-src` → fallback content

### Redirects
Redirects will be followed when requesting fragments except they redirect from `https` to `http`.

### Caching
Fragments are considered cacheable if they have HTTP status code
   * `200`, `203`, `204`, `206`,
   * `300`,
   * `404`, `405`, `410`, `414`,
   * `501`

## Library Development

### Quick Start
* Install to local `.m2` repository
   ```console
   $ ./mvnw clean install
   ```
* Check for outdated dependencies via [Versions Maven Plugin](https://www.mojohaus.org/versions/versions-maven-plugin/index.html)
   ```console
   $ ./mvnw versions:display-dependency-updates
   ```
* Update maven wrapper to newer version
   ```console
   $ ./mvnw wrapper:wrapper -Dmaven=<version, e.g. 3.9.0>
   ```

### Tooling
* See Artifacts in [nexus repository manager](https://s01.oss.sonatype.org/index.html#nexus-search;gav~io.github.ableron~ableron~~~)

### Perform Release
1. Create new release branch (`git checkout -b release-x.x.x`)
2. Set release version in `pom.xml` (remove `-SNAPSHOT`)
3. Update version in maven and gradle dependency declaration code snippets in`README.md`
4. Merge release branch into `main`
5. Release and deploy to Maven Central is performed automatically
6. Manually create [GitHub Release](https://github.com/ableron/ableron-java/releases/new)
   1. Set tag name to the version declared in `pom.xml`, e.g. `v0.0.1`
   2. Set release title to the version declared in `pom.xml`, e.g. `0.0.1`
   3. Let GitHub generate the release notes automatically
   4. Publish release
7. Set artifact version in `main` branch to next `-SNAPSHOT` version via new commit

## Contributing
Contributions are greatly appreciated. To contribute you can either simply open an issue or fork the repository and create a pull request:
1. Fork this repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Added some amazing feature'`)
4. Push to your branch (`git push origin feature/amazing-feature`)
5. Open a pull request
