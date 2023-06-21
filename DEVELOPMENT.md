## Library Development

### Quick Start
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
