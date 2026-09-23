# Common services

Each consuming plugin owns its service instances. EarthPolLib is shaded into that plugin; it has no
global plugin instance or shared runtime registry. Existing utility and translation entry points remain available.

## 1. Scheduling and cleanup

Create a `PluginResources` registry during enable and close it during disable. Register dependencies
before the services that use them: cleanup runs in reverse order and attempts every resource even if one throws.

```java
private PluginResources resources;
private PluginScheduler tasks;

@Override
public void onEnable() {
    resources = new PluginResources();
    EnhancedLogger log = resources.register(EnhancedLogger.create(this, "example"));
    // Register the database service here if the plugin uses one.
    tasks = resources.register(new PluginScheduler(this));
}

@Override
public void onDisable() {
    if (resources != null) resources.close();
}
```

`DatabaseManager`, `DatabaseService`, `EnhancedLogger`, and `LogRetentionTask` implement `AutoCloseable`.
Register the service that owns a database manager, rather than registering both for the same pool.
Use `resources.onClose(...)` for other cleanup callbacks. A resource registered after closure is closed immediately and rejected.

`PluginScheduler` provides `runEntity`, `runRegion`, `runGlobal`, and `runAsync`, each with `Delayed`
and `AtFixedRate` variants. Region methods take a location; entity methods take the entity itself.
Global tasks are for work belonging to no particular region. Paper supports these same scheduling APIs.

```java
TaskHandle reminder = tasks.runEntityDelayed(player, 100, () ->
        player.sendMessage(Component.text("Reminder.")));
// Tick delays measure server ticks; elapsed time varies with server tick rate.
reminder.cancel();

TaskHandle cleanup = tasks.runAsyncAtFixedRate(
        Duration.ofSeconds(10), Duration.ofMinutes(1), this::cleanExpiredData);
```

Tick delays and periods must be positive. Async methods use `Duration`; their initial delay may be zero.
`TaskHandle.completion()` finishes after a one-shot action, or exceptionally on failure, cancellation,
or entity retirement. Repeating tasks remain pending until stopped. Closing a scheduler rejects new work
and cancels outstanding handles; it does not interrupt actions already running. No completion callback
has a guaranteed execution thread: explicitly schedule any entity/world access, including error handlers.
Close the scheduler even though Paper also cancels plugin tasks, so waiting completion stages are settled.

## 2. Messages

Use the existing `TranslationService` to load locales, then choose a template format explicitly:

```java
TranslationService translations = new TranslationService(this, getClass());
translations.load();
Messages messages = new Messages(translations, Messages.Format.MINI_MESSAGE);
messages.success(player, "town.created", Map.of("name", townName));
```

An example `translations/en-US.yml` bundled by the consuming plugin:

```yaml
general:
  prefix: '<gold>[EarthPol]</gold> '
town:
  created: 'Created <name>.'
command:
  player-only: 'You must be a player to use this command.'
  denied: 'You need <permission> to use this command.'
  quantity: 'Enter a whole number between <min> and <max>.'
  usage: 'Usage: <usage>'
```

The default `Messages(translations)` constructor uses legacy `&` colors and `{name}` placeholders.
MiniMessage placeholders use lowercase names. Values are literal text or Adventure components, so
player-supplied names cannot inject formatting. Component values retain click/hover events.
Templates are trusted plugin configuration. Existing `Translations` methods still use legacy colors
and positional `MessageFormat` arguments.

`send` accepts `MessageStyle.INFO`, `SUCCESS`, `WARNING`, or `ERROR`; `success`, `warning`, and `error`
are shortcuts. Styles provide fallback colors without replacing colors explicitly set by a template.
`actionBar` omits the chat prefix. `component` renders a translation without a prefix or fallback style;
`format` includes both. Pass a null prefix key to the three-argument constructor to omit chat prefixes.
Send messages from the sender's owning thread.

## 3. Commands

`CommandUtil` adds separate player/permission checks, integer validation, and translated feedback:

```java
if (!CommandUtil.isPlayerAndHasPermission(sender, "example.create", messages,
        "command.player-only", "command.denied")) return;
OptionalInt quantity = CommandUtil.parseInteger(sender, input, 1, 64, messages, "command.quantity");
if (quantity.isEmpty()) return;
```

Permission helpers retain the original operator bypass. Component overloads let callers provide their
own feedback; a null component suppresses it. `sendUsage` takes a translation key and a literal usage string.

`CommandPage.of(items, pageNumber, pageSize)` returns a read-only page and total counts. Pages are
one-based; an empty collection has one empty page. Invalid page numbers/sizes are rejected.
Use `page.navigation(number -> "/example list " + number, previousLabel, nextLabel)` for clickable controls.

Register one `ConfirmationManager` per plugin. Requests replace the player's previous pending action
and return a unique token; include that token in the confirmation command:

```java
UUID token = confirmations.request(player.getUniqueId(), Duration.ofSeconds(30),
        () -> player.hasPermission("example.delete"),
        () -> deleteSelectedRecord(recordId));
// Later, on the player's command thread:
ConfirmationManager.Result result = confirmations.confirm(player.getUniqueId(), suppliedToken);
```

Handle `CONFIRMED`, `NOT_FOUND`, `EXPIRED`, and `DENIED` with translated feedback. Authorization is
checked at confirmation time; include current ownership/target validity checks in that predicate too.
The action is consumed at most once, including when it throws. An old token cannot confirm a newer request.
Call `cancel(playerId)` on quit and close the manager on disable. Expired requests are purged on new
requests; `purgeExpired()` is also available for periodic cleanup. Closing cannot undo an action already running.

## 4. Database readiness and transactions

Register `DatabaseService` for cleanup, then use `initializeAsync()` to start its pool and run migrations
off tick threads. The returned stage contains the number of applied migrations. Concurrent calls share
one attempt; a failed attempt can be explicitly retried if the plugin remains enabled.
`migrateAsyncResult()` exposes the same completion result for an already started pool.
The original `start()` and `migrateAsync()` entry points remain available.

Call initialization before submitting queries. `queryAsync` and `transactionAsync` wait for that attempt
and propagate its failure without running SQL. Their callbacks receive a borrowed connection and must
return materialized data, closing their own statements/result sets:

```java
database.initializeAsync();
CompletionStage<Integer> result = database.transactionAsync(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE example_players SET display_name = ? WHERE player_id = ?")) {
        statement.setString(1, requestedName);
        statement.setString(2, playerId.toString());
        return statement.executeUpdate();
    }
});
```

The example table belongs to the consuming plugin and must be created by its migrations. Capture
player data before submitting work; SQL callbacks must not access Bukkit world/entity state. Reschedule
any player response through `PluginScheduler`, and observe exceptional completion without blocking a tick thread.
`isReady()` is a non-blocking state check; `isRunning()` retains its existing database ping behavior.

For callers that already control execution, `DatabaseManager.withConnection`, `DatabaseManager.transaction`,
and `SqlTransactions.execute(DataSource, SqlWork)` are synchronous alternatives. Transactions commit success,
roll back failures, preserve cleanup errors as suppressed exceptions, and close the borrowed connection.
Callbacks must not close connections or manage transaction boundaries. MariaDB statements that implicitly
commit, including DDL, cannot be rolled back by these helpers. There are no automatic SQL retries.
Closing the service cancels queued work and closes the pool; SQL already running may have taken effect.

## 5. Validated configuration reloads

Add pure predicates and optional restart metadata when declaring nodes:

```java
ReloadableConfigNode<Integer> limit = ReloadableConfigNode.of("limit", Integer.class, 10)
        .validateWith(ConfigValidators.range(1, 100), "must be between 1 and 100");
ReloadableConfigNode<String> mode = ReloadableConfigNode.of("mode", String.class, "safe")
        .validateWith(ConfigValidators.oneOf(List.of("safe", "fast")), "must be safe or fast");
ReloadableConfigNode<String> databaseName = ReloadableConfigNode.of("database", String.class, "earthpol")
        .validateWith(ConfigValidators.nonBlank(), "must not be blank")
        .restartRequired();
```

Rules also apply to programmatic assignments. Invalid defaults cannot install a rule. List nodes
continue to validate element types and copy their values. Custom node types should put validation in
`validateValue`, which validated reloads call before committing the staged values.

`handler.reloadValidated()` returns `ConfigReloadResult`:

- Invalid YAML or any invalid value leaves all active values and the handler's YAML unchanged.
- `errors()` maps configuration paths to diagnostics. File syntax errors use the `<file>` key.
- Valid live values are applied together after validation; missing keys use defaults in memory.
- Restart-only changes are reported in `restartRequired()` while retaining their old active values.
- This method never rewrites the file. Call `save()` explicitly when desired.

`handler.snapshot()` captures a read-only map consistently against handler reload/set operations.
Use one snapshot when several settings must come from the same reload. Direct enum/node reads can
observe separate updates, and direct node mutation bypasses the handler's coordination. Snapshot values
of custom mutable types are not deep-copied.

The original `reload()` keeps its per-key default fallback and now honors opt-in validators and
restart metadata. For restart-only settings, `yaml()` represents requested file values while nodes
and snapshots represent values currently active in the plugin.
