# Project generator

Run `python3 generator/server.py --base-version VERSION` and open
`http://127.0.0.1:8080`. Supply a version already available in Maven Central.
The page shows the tested Java and Spring Boot baseline and the features implied
by a selection.

For the CLI, save JSON project options with `name`, `group`, `artifact`,
`package`, `database` (`none` or `postgresql`), and `features` (IDs from
`catalog.json`). Run `python3 generator/project_generator.py options.json
--base-version VERSION --output application.zip`.

The generator emits a standalone Maven application with the wrapper,
a pinned BOM, and the selected dependencies. The listener binds to loopback,
and the download endpoint builds ZIPs in memory.

The catalog lists each selectable starter or library, prerequisite features,
compatible database settings, and the direct Spring Boot starters brought in by
its implementation. Required features are added automatically. Generated
application documentation includes setup steps for the features you select.
