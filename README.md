# Ableron Java Library
[![Build Status](https://github.com/ableron/ableron-java/actions/workflows/main.yml/badge.svg)](https://github.com/ableron/ableron-java/actions/workflows/main.yml)
[![License](https://img.shields.io/github/license/ableron/ableron-java)](https://github.com/ableron/ableron-java/blob/main/LICENSE)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.ableron/ableron/badge.svg)](https://mvnrepository.com/artifact/io.github.ableron/ableron)
[![GitHub Release](https://img.shields.io/github/v/release/ableron/ableron-java.svg)](https://github.com/ableron/ableron-java/releases)
[![Java Version](https://img.shields.io/badge/Java-11+-4EB1BA.svg)](https://docs.oracle.com/en/java/javase/11/)

Java Library for Ableron Server Side UI Composition

## Installation
Maven:
```xml
<dependency>
  <groupId>io.github.ableron</groupId>
  <artifactId>ableron</artifactId>
  <version>0.0.1</version>
</dependency>
```
Gradle:
```groovy
dependencies {
  implementation 'io.github.ableron:ableron:0.0.1'
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
       <ableron-include src="https://load-head-fragment-from-here" />
     </head>
     <body>
       <ableron-include src="https://load-body-fragment-from-here" />
     </body>
   </html>
   ```
1. Apply transclusion to response if applicable (HTTP response status 2xx, 4xx or 5xx; Response content type is non-binary, ...)
   ```java
   TransclusionResult transclusionResult = ableron.applyTransclusion(originalResponseBody);
   String processedResponseBody = transclusionResult.getContent();
   ```

### Configuration Options
* `enabled`: Whether UI composition is enabled. Defaults to `true`
* `requestTimeout`: Timeout for HTTP requests. Defaults to `5 seconds`
* `fallbackResponseCacheTime`: Duration to cache HTTP responses in case neither `Cache-Control` nor `Expires` header is present. Defaults to `5 minutes`

### Include Tag
* Must be closed, i.e. either `<ableron-include ... />` or `<ableron-include ...></ableron-include>`
* Content between `<ableron-include>` and `</ableron-include>` is used as fallback content
* Attributes
   * `src`: URL to load the include content from
   * `src-timeout-millis`: Timeout for requesting the `src` URL. Defaults to global `requestTimeout`
   * `fallback-src`: URL to load the include content from in case the request to `src` failed
   * `fallback-src-timeout-millis`: Timeout for requesting the `fallback-src` URL. Defaults to global `requestTimeout`
* Precedence for resolving: `src` -> `fallback-src` -> fallback content

### Redirects
Redirects will be followed when resolving includes except they redirect from `https` to `http`.

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
