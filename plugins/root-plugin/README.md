# Eva Root Dispatcher Plugin

This system plugin supplies the single `topic="/"` dispatcher discovered by
the host runtime. It sends each inbound event to every child dispatcher except
the reporting dispatcher (the `/CMD` dispatcher is the built-in reporting
endpoint). When an event identifies its source provider, the child dispatcher
that owns that provider is also excluded.

Load this plugin together with the business plugins that provide the child
dispatchers. The host `Booter` still enforces that exactly one root dispatcher
is loaded.

Build the release package from the `eva-cli` directory:

```sh
./gradlew :plugins:root-plugin:assembleEvaPlugin
```
