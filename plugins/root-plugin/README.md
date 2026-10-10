# Eva Root Dispatcher Plugin

This system plugin supplies the single `topic="/"` dispatcher discovered by
the host runtime. It routes events to one child dispatcher at a time:

- an event with `metadata.targetTopic` uses that topic;
- an event without a target uses `/Echo` as the default;
- the host may override that default with the `eva.root.defaultTopic`
  preference;
- a missing or ambiguous target fails with a clear routing error.

Set the preference on the loaded root plugin context before `Booter.start()`
when the application uses an ingress topic other than `/Echo`.

Load this plugin together with the business plugins that provide the child
dispatchers. The host `Booter` still enforces that exactly one root dispatcher
is loaded.

Build the release package from the `eva-cli` directory:

```sh
./gradlew :plugins:root-plugin:assembleEvaPlugin
```
