## Coding Guidelines
### Generic guidelines
1. Guard early - Return errors at function top.
2. Balance conciseness, readability and idiomatic code, preferring conciseness.
3. Prefer expression-based logic to statement-based logic where possible.
4. Avoid temporary variables, unless used in multiple places.
5. Use code comments only for complex logic.
6. Write code as if for production. Never take shortcuts.
### Java Coding Guidelines
- Prefer final in properties, which promotes immutable classes.
- Only one simple constructor, that all other constructors call.
- Prefer naming classes and interfaces as one-word nouns, avoiding "agent nouns", like xManager and xHelper. xService is also generally pointless, but has uses.
- The code in the core of the app should read like a DSL. Stupid simple. 
- Don't hesitate to use an object as a function, for example `new Action(param).execute();`.
- Use Records, over anemic class.
- No getters, never setters. Getters may exist, but are named like properties of Java records.
- Use generics sparingly.
- No frameworks, no annotations.
- Records over classes with getters and setters
- Java streams, where appropriate
- Use var 
- Avoid class casting

### Ports & Adapters (Hexagonal) Architecture

- The core has zero dependencies on frameworks.
- The core depends on interfaces that are dependency injected, usually manually, to the constructor (in 99% of cases).
- There are two types of ports, defined by Java interfaces:
    - Driving ports: through which a CLI, web, or test can invoke methods of the core. May also be called API (Application Programming Interface).
    - Driven ports:  Always defined **IN THE LANGUAGE OF THE CORE**. This promotes a DSL-like approach. May also be called a SPI (Service Provider Interface).
- **Driven Adapters** (like a Database Repository) must map their specific database entities (e.g., ORM models) into **Core Domain Objects** before returning them to the Core.
- The Core should be able to run in a "headless" state. If you can't run your entire business logic through a unit test without starting a database or a web server, the architecture isn't truly hexagonal.
- **However**, differing from traditional Hexagonal architecture, this codebase will not use interfaces where there is only one implementation
## Agent Rules & Workflow
1. **Drafting Phase**: When a task is assigned, use the primary coding model to generate the logic.
2. **Internal Review (MANDATORY)**: Before showing me the final code or running a 'write' command, you must explicitly invoke the `Reviewer` sub-agent.
3. **Final Output**: If the sub-agent finds an issue, fix it *internally* first. Only present the polished, reviewed version to me. 
4. **Transparency**: Briefly mention: "Checked for blind spots with Reviewer agent — \[Status: Clear/Fixed]."
