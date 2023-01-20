# Ableron Java Library
[![Build Status](https://github.com/ableron/ableron-java/actions/workflows/main.yml/badge.svg)](https://github.com/ableron/ableron-java/actions/workflows/main.yml)
[![License](https://img.shields.io/github/license/ableron/ableron-java)](https://github.com/ableron/ableron-java/blob/main/LICENSE)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.ableron/ableron/badge.svg)](https://mvnrepository.com/artifact/io.github.ableron/ableron)

Java Library for Ableron Server Side UI Composition

## Using the library
1. Add Ableron Java library to your project
   * Maven `pom.xml`
   ```xml
   <dependency>
     <groupId>io.github.ableron</groupId>
     <artifactId>ableron</artifactId>
     <version>0.0.1</version>
   </dependency>
   ```

   * Gradle `build.gradle`:
   ```groovy
   dependencies {
     implementation 'io.github.ableron:ableron:0.0.1'
   }
   ```
2. Apply transclusion to response bodies
   * Check whether response body is suitable to be handled, e.g.
     * HTTP response status 2xx, 4xx or 5xx
     * Response content type is non-binary
   * Apply transclusion
   ```java
   var ableronConfig = new AbleronConfig();
   ableronConfig.put(AbleronConfigParams.ENABLED, true);
   var ableron = new Ableron(ableronConfig);
   TransclusionResult transclusionResult = ableron.applyTransclusion(originalResponseBody);
   String processedResponseBody = transclusionResult.getBody();
   ```

## Using fragments in response body
  ```html
  <fragment src="https://your-fragment-url" />
  ```

## Developing the library
* Install to local `.m2` repository
  ```console
  $ ./mvnw clean install
  ```
* Check for outdated dependencies via [Versions Maven Plugin](https://www.mojohaus.org/versions/versions-maven-plugin/index.html)
  ```console
  $ ./mvnw versions:display-dependency-updates
  ```

## Perform release
1. Create new release branch (`git checkout -b release-x.x.x`)
2. Set release version in `pom.xml` (remove `-SNAPSHOT`)
3. Merge release branch into `main`
4. Release and deploy to Maven Central is performed automatically
5. Manually create [GitHub Release](https://github.com/ableron/ableron-java/releases/new)
   1. Set tag name to the version declared in `pom.xml`, e.g. `v0.0.1`
   2. Set release title to the version declared in `pom.xml`, e.g. `0.0.1`
   3. Let GitHub generate the release notes automatically
   4. Publish release
6. Set artifact version in `main` branch to next `-SNAPSHOT` version via new commit
