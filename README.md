# EarthPolLib

EarthPolLib is EarthPol's shared Paper development library. It is intended to be shaded into other plugins at build time rather than loaded as a standalone server plugin.

Java packages use the `com.earthpol.earthpollib` namespace.

## Build

Use Java 21 or newer and Maven:

```bash
mvn -B verify
```

Use `mvn -B package` when you want the release jars under `target/`.

## Consume EarthPolLib

EarthPolLib is a library jar. It should be shaded into the plugin that uses it.

Add the Bitworks Nexus repository and the dependency to your plugin. Use the version from
the [latest release](https://github.com/EarthPol/EarthPolLib/releases/latest):

```xml
<repositories>
    <repository>
        <id>bitworks-releases</id>
        <url>https://nexus.tinydc.net/repository/maven-releases/</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.earthpol</groupId>
    <artifactId>earthpollib</artifactId>
    <version>1.0.0</version>
</dependency>
```

For local development, run `mvn -B install` in this project to install the version in `pom.xml`.

Notes:

- Do not drop `EarthPolLib` onto a Paper server as its own plugin. There is no `plugin.yml`.
- `paper-api` and `towny` stay `provided`.
- MariaDB is shaded into the EarthPolLib artifact under `com.earthpol.libs.mariadb`, and bundled SQL migrations are handled by EarthPolLib's internal schema migrator.

## Included Modules

- Utilities: `annotation`, `command`, `location`, `math`, `string`, `entity.vehicle`
- Developer config support: `config`, `translation`, `messaging`, `cooldown`
- Service support: `lifecycle`, `scheduling`, `logging`, `database`, `database.migration`
- Gameplay support: `teleport`

See [Common services](docs/common-services.md) for task ownership and cleanup, component messages,
command checks and confirmations, asynchronous database readiness, and validated configuration reloads.

## Database Defaults

The current database layer intentionally keeps the existing JDBC behavior. The baseline today is:

- MariaDB driver with HikariCP
- `tcpKeepAlive=true`
- `sessionVariables=character_set_client=utf8mb4,character_set_results=utf8mb4`
- Hikari defaults tuned for a small local plugin pool

JDBC parameter customization is additive through `DatabaseManager` and preserves that baseline unless a caller opts into changes.

## Release Model

- Work on feature branches and open pull requests into `main`. Branch protection requires the
  GitHub Actions `build` check, an up-to-date branch, and resolved review conversations, including
  for administrators. Force pushes and branch deletion are blocked. A second reviewer is optional.
- Every push to `main` triggers the release workflow. It verifies the code, reserves a version tag,
  publishes `com.earthpol:earthpollib` to Bitworks Nexus, and creates a GitHub release with the main,
  source, and javadoc jars, the published POM, and SHA-256 checksums.
- The first release uses `1.0.0`. Later main updates automatically increment the highest release
  tag's patch version. Raise the version in `pom.xml` to start a new minor or major release.
  The POM version is a minimum; CI sets the actual artifact version without committing back to main.
- Releases run sequentially, with pending runs queued. Tags identify the source commit and reserve
  its version for retries. A new release must include the previous release commit.
- Publishing uses the same `NEXUS_USERNAME` and `NEXUS_PASSWORD` secrets as HeadDB. They can be
  repository secrets or organization secrets shared with `EarthPol/EarthPolLib`. Maven's server ID
  is `nexus-releases`; the snapshot repository is configured for explicit development deployments.

To retry, rerun the failed Action or run **Release** manually on `main` with its tag, such as
`v1.0.0`. If all four artifacts are already on Nexus, the workflow reuses those exact bytes for
the GitHub release. Partial Nexus uploads or pre-existing versions without a matching reserved tag
stop publication and require the conflicting Nexus version to be resolved before retrying.

To reproduce an artifact from a tag, check it out, set the corresponding Maven version, then build:

```bash
mvn -B -ntp org.codehaus.mojo:versions-maven-plugin:2.22.0:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
mvn -B -ntp clean verify
```
