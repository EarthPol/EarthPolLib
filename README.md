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

For local development, run `mvn -B install` in this project, then add the dependency to your plugin:

```xml
<dependency>
    <groupId>com.earthpol</groupId>
    <artifactId>earthpollib</artifactId>
    <version>1.0.0</version>
</dependency>
```

Notes:

- Do not drop `EarthPolLib` onto a Paper server as its own plugin. There is no `plugin.yml`.
- `paper-api` and `towny` stay `provided`.
- MariaDB is shaded into the EarthPolLib artifact under `com.earthpol.libs.mariadb`, and bundled SQL migrations are handled by EarthPolLib's internal schema migrator.

## Included Modules

- Utilities: `annotation`, `command`, `location`, `math`, `string`, `entity.vehicle`
- Developer config support: `config`, `translation`, `cooldown`
- Service support: `logging`, `database`, `database.migration`
- Gameplay support: `teleport`

## Database Defaults

The current database layer intentionally keeps the existing JDBC behavior. The baseline today is:

- MariaDB driver with HikariCP
- `tcpKeepAlive=true`
- `sessionVariables=character_set_client=utf8mb4,character_set_results=utf8mb4`
- Hikari defaults tuned for a small local plugin pool

JDBC parameter customization is additive through `DatabaseManager` and preserves that baseline unless a caller opts into changes.

## Release Model

- Pushes and pull requests to `main` run CI only.
- GitHub releases are created from version tags such as `v1.0.0`.
- Tagged releases publish `com.earthpol:earthpollib` to GitHub Packages for `EarthPolForever/EarthPolLib`.
- The release workflow uploads the jars built in `target/`, including the main artifact and attached source or javadoc jars.

To release, update the version in `pom.xml`, run `mvn -B verify`, and push a matching `v<version>` tag. The workflow checks that the tag matches the Maven version before publishing.
