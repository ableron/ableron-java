# Ableron Java Library
![Build Status](https://github.com/ableron/ableron-java/actions/workflows/main.yml/badge.svg)
![License](https://img.shields.io/github/license/ableron/ableron-java)

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
