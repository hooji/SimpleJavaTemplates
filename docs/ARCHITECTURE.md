# SimpleJavaTemplates — Architecture

## What it is

A tiny Java library that lets you write string templates that reference the
caller's local variables by name:

```java
String name = "World";
int n = 3;
System.out.println(Template.f("Hello, {name} (x{n})!"));   // Hello, World (x3)!
```

Plain Java has no way to introspect a caller's local-variable values, so the
library ships as a Java agent (`-javaagent:SimpleJavaTemplates-*.jar`). At
class-load time the agent rewrites every call site of an annotated stub method
to also pass a `Map<String, Object>` snapshot of the caller's locals.

The same machinery powers a SQL helper — `Template.sql/query/queryRows/update/...`
— that compiles the template into a `PreparedStatement` with `?` placeholders
and bound values, so user-supplied locals can never become SQL injection.

## Module layout

```
src/main/java/ai/jacc/simplejavatemplates/
├── Template.java                       — public API: f(...), sql(...), query(...), ...
├── TemplateExpander.java               — placeholder parser + expression evaluator
├── RequiresCallerLocalVariableDetails  — marker annotation (the rewrite trigger)
├── LocalVariableDetails                — per-local metadata (name, slot, PC range)
├── MethodLocalVariableDetails          — array wrapper, stored in a synthetic static field
├── TemplateException / AgentNotLoadedException
└── agent/
    ├── SimpleJavaTemplatesAgent        — premain entry point
    ├── CallerLocalVariableTransformer  — ClassFileTransformer; the orchestrator
    ├── RawBytecodeScanner              — raw classfile parser (PCs, LVT)
    ├── MethodRewriter                  — slot renumbering + call-site rewriting
    ├── LogicalVariable                 — one source-level local after renumbering
    └── MapBuilder                      — runtime helper; called by rewritten code
```

The jar is shaded (ASM is bundled) and its manifest declares
`Premain-Class: ai.jacc.simplejavatemplates.agent.SimpleJavaTemplatesAgent`,
so the same artifact is both library and agent.

## The two halves

### 1. Compile-time / load-time: the bytecode transform

Every public template method on `Template` exists as a **stub/companion pair**:

| Stub (what the user calls)                      | Companion (what runs after rewrite)                                                  |
|-------------------------------------------------|--------------------------------------------------------------------------------------|
| `@RequiresCallerLocalVariableDetails`           | extra leading `Map<String,Object> localVarValues` parameter                          |
| body throws `AgentNotLoadedException`           | real implementation                                                                  |
| name e.g. `f`                                   | name e.g. `$___f__Ljava_lang_String_2___` (encoded to be a legal JVM identifier)     |

If the agent isn't loaded, the stub body runs and the user gets a clear error.
If the agent *is* loaded, the agent rewrites every caller so the stub call is
replaced with a call to the companion — the stub method itself is never
modified.

#### `SimpleJavaTemplatesAgent.premain`

1. Guards against being installed twice (a sibling agent like
   *DurableJavaThreads* may detect SJT on the classpath and chain into this
   `premain`; double-registration would create two transformers).
2. Registers `CallerLocalVariableTransformer`.
3. Eagerly `Class.forName("...Template")` so the transformer sees its
   annotated methods before any user class is loaded.

#### `CallerLocalVariableTransformer.transform`

Runs once per class that the JVM loads (skipping JDK and agent-internal
classes). Three logical phases:

**Phase 1 — annotation registration.** Scan the incoming class's classfile for
methods bearing `@RequiresCallerLocalVariableDetails` and remember them in
`annotatedMethods` (key: `owner.name.desc`). For each annotated method, look in
the same class for the matching companion `$___name__params___`; if it's
missing, store a pre-built diagnostic in `missingCompanionMessages` so call
sites can be rewritten into a clear `TemplateException` instead of silently
calling the stub. (This is what enables third parties to define their own
annotated helpers — see `CustomAnnotatedMethodTest`.)

**Phase 1b — pre-register referenced classes.** A subtle load-order race
exists: if a *caller* is loaded before its callee's class is loaded, the
caller's transform won't yet know that the callee is annotated and will leave
the call site alone — the JVM will then later run the stub body and throw
`AgentNotLoadedException`. To prevent this, the transformer walks every
`MethodInsnNode` in the class being loaded, fetches the bytes of every
referenced owner class via the classloader, and runs the annotation scan on
each. `scannedClasses` deduplicates this work. (`LoadOrderTest` exercises this
path.)

**Phase 2 — quick scan.** Walk the class with `SKIP_DEBUG | SKIP_FRAMES`
checking whether any method invocation targets an annotated method. If none,
return `null` (no transform).

**Phase 3 — full transform.** Build a tree-API `ClassNode` and rewrite each
affected method (see below). Inject one synthetic `private static final
MethodLocalVariableDetails` field per rewritten method, holding the metadata
the runtime needs. Extend (or create) the class's `<clinit>` to populate those
fields. Emit with `COMPUTE_FRAMES` so the verifier-visible
`StackMapTable` is recomputed from scratch.

#### Per-method rewrite — the heart of the transformer

The hard part is preserving every local variable's value at the call site,
even though normal javac-generated bytecode aggressively reuses slots. The
strategy ("Strategy B" per the source comment) is **slot renumbering** — give
every source-level local its *own* permanent slot for the entire method, so
every store survives until the call site reads it.

`MethodRewriter` and `RawBytecodeScanner` cooperate to do this:

1. **Raw bytecode scan.** ASM's tree API doesn't expose original PCs.
   `RawBytecodeScanner` walks the classfile's raw `Code` attribute using a
   per-opcode size table (with special cases for `tableswitch`,
   `lookupswitch`, and `wide`) to compute `instructionPCs[]` and pull the raw
   `LocalVariableTable` entries.
2. **Correlate AST nodes ↔ original PCs.** Each non-pseudo `AbstractInsnNode`
   in the `InsnList` is paired by index with the raw PC array, producing an
   `IdentityHashMap<AbstractInsnNode, Integer>`.
3. **Build `LogicalVariable`s.** Parameters keep their original slots;
   non-parameters get fresh slots starting at `originalMaxLocals`. Two LVT
   entries with the same `(slot, name, descriptor)` — javac splits an LVT
   range like this for pattern variables and other flow-typed locals — share
   the same new slot, otherwise stores would land in one slot and reads come
   from the other.
4. **Capture original PCs of annotated call sites *before* any rewriting.**
   Once we start inserting instructions, ASM-side PCs shift; original PCs are
   what we need at runtime to determine which locals are in scope.
5. **Renumber slots.** For every `VarInsnNode` / `IincInsnNode`, look up the
   logical variable that owns `(oldSlot, originalPc)` and rewrite `var` to its
   new slot. There's a 4-byte fudge for the "initialization store" javac emits
   immediately *before* an LVT range begins (the LVT `start_pc` typically
   points to the instruction *after* the store).
6. **Emit slot initializers.** `COMPUTE_FRAMES` types every fresh slot as
   `top` until the verifier sees a store to it. Because the rewritten call
   site reads *every* logical's slot up front and the runtime filters by PC
   range, every fresh slot must be pre-initialized to a valid default at
   method entry.
7. **Rewrite each call site.** For each annotated invocation:
   - Pop the existing stub args into temporary slots above the new `maxLocals`.
   - Push: the metadata field, the original PC constant, and an `Object[]`
     populated with the boxed value of every metadata logical's slot.
   - `INVOKESTATIC MapBuilder.buildMap(...)` returns a `Map<String,Object>`.
   - Reload the saved stub args from the temp slots.
   - Retarget the invoke to the synthetic companion (encoded name and a
     descriptor with a `Map` prepended).
   - If the companion is missing, replace the call with a `pop` of the
     pushed args plus `throw new TemplateException(message)` carrying a
     diagnostic that names the exact companion signature the user must add.
8. **Update the LVT** so the verifier sees one entry per `(newSlot, name,
   descriptor)` covering the whole method (deduping shared-slot splits to
   avoid `ClassFormatError`).

The companion is invoked with the same stack shape the user's callsite
produced, just with one extra leading `Map` argument.

### 2. Runtime: turning the map into a string

`MapBuilder.buildMap` is the only piece of the agent package called from
rewritten user code. It receives:
- the per-method metadata (one `LocalVariableDetails` per logical),
- the original PC of the call site (constant baked in at rewrite time),
- a parallel `Object[]` of boxed slot values.

It returns a `Map<String, Object>` filtered to logicals whose original PC
range covers the call site's PC, with **inner-scope-wins shadowing**: if two
logicals share a name (because of nested blocks), the one with the later
`originalStartPc` wins. Compiler-synthetic names (those containing `$`) are
filtered out as defense in depth.

`Template.f(...)` then delegates to `TemplateExpander.expand(map, template)`,
which is a small handwritten parser:
- `{name}` / `${name}` — variable lookup, with `${{...}}` for nested
  templates.
- `{name:fmt}` — `String.format("%fmt", value)`.
- `{?name}` — optional placeholder; if `null`, swallow a trailing newline so
  removing an optional line removes the line.
- Dot expressions — `{user.name}` resolves via public field, then `name()`,
  then `getName()`, then `isName()`.
- Containers (arrays / `Collection`) render as `[a, b, c]`.
- Modes: `requireLeadingDollar` (forbid bare `{...}`),
  `optionalPlaceholders`, `expandContainers`, `memberAccess` — all exposed
  via fluent setters on `TemplateExpander`. The `Template.f` static API uses
  a single global `TemplateExpander`; users wanting per-instance options
  construct their own and call `expander.f(...)`.

### 2b. The SQL path

`Template.sql(conn, "SELECT ... WHERE id = {userId}")` and friends share the
expression-evaluation step but emit a `PreparedStatement` instead of a
`String`. `parseSqlTemplate` walks the template (respecting the same
`requireLeadingDollar` mode), and for each `{...}`:
- Scalar value → append `?`, add value to bind list.
- Container value → expand to `(?, ?, ?)`, add each element.
- Empty container → `(NULL)` so `IN ()` is still legal SQL.

Connection-less overloads (`Template.sql(template)`) call `findConnection`
which scans the locals map for the first `Connection` (preferring `conn` /
`connection` by name); when none is present it throws a
`TemplateException` enumerating the available locals and pointing at the
explicit overload.

`queryRows` returns each row as a `LinkedHashMap<String,Object>` so keys
preserve column order. `insertAndReturnLongKey` uses
`RETURN_GENERATED_KEYS`. `query` returns a `ResultSet` whose backing
`PreparedStatement` has `closeOnCompletion()` set so the caller only has to
close the `ResultSet`.

## Build & distribution

- Maven, JDK 1.8 source/target for main code (so the agent is loadable on any
  JVM ≥ 8). Tests default to 1.8 too; on JDK ≥ 21 the test compile is bumped
  to 21 to pull `PatternMatchingTest` in.
- ASM 9.9.1 is shaded into the released jar via `maven-shade-plugin` so users
  don't have to add ASM themselves.
- `maven-jar-plugin` writes the manifest's `Premain-Class`,
  `Can-Retransform-Classes`, `Can-Redefine-Classes`.
- Tests use JUnit 4 and H2 (in-memory) for SQL coverage. Notable suites:
  `SmokeTest` (basic templating), `SqlTest` (SQL path),
  `ScopeResolutionTest` (inner-scope-wins), `PatternMatchingTest` (split-LVT
  pattern variables), `LoadOrderTest` (the cross-class load-order race),
  `ParanoidConcurrencyTest` / `ParanoidControlFlowTest` /
  `ParanoidTypesTest` (stress on the slot renumbering),
  `CustomAnnotatedMethodTest` (third-party annotated method).

## Why this design

- **Stub + companion split** keeps the API a normal Java method call: the
  IDE, javac, and any non-agent runtime see ordinary code.
  Misconfiguration produces an explicit, actionable exception rather than a
  silent miscompile.
- **Slot renumbering** is the simplest scheme that survives arbitrary
  control flow: every local has its own slot for the whole method, and the
  rewritten call site can read all of them. The runtime — not the
  transformer — does the in-scope filtering, using PC ranges that were
  captured in the *original* bytecode and baked in as constants. That keeps
  the transform local: it never has to update PC-relative metadata for the
  shifts its own inserted code introduces.
- **Raw classfile parsing** for PCs and LVT side-steps ASM's deliberate
  abstraction over offsets while still letting the rewrite use ASM's tree
  API for everything else.
- **Per-method synthetic field + `<clinit>` extension** keeps the metadata
  one constant lookup away at runtime; no reflection on the hot path.
- **Pre-registering referenced class annotations** is what makes the
  transform robust to JVM class-loading order, which the agent doesn't
  control.
