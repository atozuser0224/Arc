# Arc Bucket Rebrand Design

## Goal

Ship this repository as Arc Bucket: every user-visible runtime, artifact,
command, documentation, and CI surface identifies the server as Arc while
retaining upstream compatibility where changing identifiers would break
plugins, configuration, or patch maintenance.

## Branding Boundary

Arc is the product identity. Paper, Gale, Purpur, and Leaf remain documented
only as upstream projects and compatibility ancestry.

The following become Arc:

- server build brand ID and brand name;
- console application name, startup banner, version output, crash diagnostics,
  metrics product name, replay metadata, and watchdog messages;
- generated Paperclip/Bundler artifact names;
- root project name, release names, workflow labels, and public documentation;
- built-in server command labels, descriptions, messages, and permissions;
- new configuration headers and default file names where migration is safe.

The following remain as compatibility internals:

- Java packages under `org.dreeam.leaf`;
- existing public Leaf API classes referenced by third-party plugins;
- legacy `leaf.*` JVM properties and old configuration files, accepted as
  aliases or migration inputs;
- patch attribution comments and upstream license/credit references.

## Startup Experience

`PaperBootstrap` prints a colored Arc Bucket ASCII banner before ordinary
bootstrap messages. The banner includes the Minecraft version, Arc build
version, Java version, and the upstream compatibility statement.

Normal bootstrap lines use the Arc build metadata, so the server reports
`Loading Arc ...` and `This server is running Arc version ...`.

## Commands

`/arc` is the primary administration command. The existing Leaf maintenance
subcommands are available below `/arc` and the legacy `/leaf` label remains a
deprecated compatibility alias.

The command DSL must support:

- root completion at every argument depth;
- explicit subcommand completion;
- case-insensitive prefix filtering for the current token;
- permission filtering for root and subcommand candidates;
- duplicate removal with stable declaration order.

The built-in `/arc` command and the showcase plugin use the same completion
behavior and have integration coverage.

## Configuration Migration

New configuration surfaces use Arc names. If an Arc file does not exist and a
legacy Leaf file does, the server loads or moves the legacy file without data
loss and logs the migration. Existing public methods named for Leaf remain as
deprecated compatibility accessors when required by binary compatibility.

## Documentation And CI

The root README becomes an Arc Bucket README with build, run, API, command, and
upstream-credit sections. GitHub workflow names, artifact names, release names,
and issue templates use Arc. Upstream repository paths and technical Gradle
task/project names remain unchanged where Paperweight requires them.

## Verification

Verification requires:

1. focused command completion tests;
2. focused branding/build metadata tests;
3. Arc API tests that can compile in the current experimental-source state;
4. Paperclip build producing an Arc-named JAR;
5. real server boot showing the ASCII banner and Arc version strings;
6. console completion checks for `/arc` and the showcase command;
7. a scan proving user-visible Leaf branding remains only in compatibility,
   migration, attribution, or upstream references.
