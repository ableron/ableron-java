# Contributing

## Quick Start
* Compile/test/package
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

## Tooling
* See Artifacts in [nexus repository manager](https://s01.oss.sonatype.org/index.html#nexus-search;gav~io.github.ableron~ableron~~~)

## Contributing
To contribute you can either simply open an issue or fork the repository and create a pull request:
1. Fork this repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Added some amazing feature'`)
4. Push to your branch (`git push origin feature/amazing-feature`)
5. Open a pull request

## Perform Release
1. Create new release branch (`git checkout -b release-x.x.x`)
1. Prepare code:
   1. Set release version in `pom.xml` (remove `-SNAPSHOT`)
   1. Update version in maven and gradle dependency declaration code snippets in`README.md`
1. Merge release branch into `main`
1. Release and deploy to Maven Central is performed automatically
1. Manually create [GitHub Release](https://github.com/ableron/ableron-java/releases/new)
   1. Set tag name to the version declared in `pom.xml`, e.g. `v0.0.1`
   2. Set release title to the version declared in `pom.xml`, e.g. `0.0.1`
   3. Let GitHub generate the release notes automatically
   4. Publish release
1. Set artifact version in `main` branch to next `-SNAPSHOT` version via new commit
