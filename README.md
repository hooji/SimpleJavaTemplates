# SimpleJavaTemplates

String templates and SQL parameterization for Java, powered by local variable capture.

Write `f("Hello {name}")` and the library reads `name` directly from your local variables — no manual parameter passing. The same mechanism drives a suite of SQL methods where `{userId}` becomes a bound `?` parameter, making SQL injection **structurally impossible**.

```java
import static ai.jacc.simplejavatemplates.Template.*;

// String templates — reads locals automatically
String name = "Alice";
int age = 30;
String msg = f("{name} is {age} years old");  // "Alice is 30 years old"

// SQL — values are bound as parameters, never concatenated into the query
Connection conn = dataSource.getConnection();
String dept = "Engineering";
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT name, email FROM employees WHERE department = {dept}");
```

## Quick Start

### 1. Get the JAR

Download `SimpleJavaTemplates.jar` from the [latest release](https://github.com/hooji/SimpleJavaTemplates/releases), or build from source:

```bash
mvn clean package
# Output: target/SimpleJavaTemplates.jar
```

### 2. Run with the Java Agent

SimpleJavaTemplates works by transforming bytecode at startup. Add the agent flag when running your application:

```bash
java -javaagent:SimpleJavaTemplates.jar -cp SimpleJavaTemplates.jar:your-app.jar com.example.Main
```

### 3. Use It

```java
import static ai.jacc.simplejavatemplates.Template.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // --- String templates ---
        String name = "World";
        System.out.println(f("Hello {name}!"));  // Hello World!

        // --- SQL templates ---
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:demo");
        conn.createStatement().execute(
            "CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(100), email VARCHAR(100))");

        // Insert
        String userName = "Alice";
        String email = "alice@example.com";
        insert(conn, "INSERT INTO users (name, email) VALUES ({userName}, {email})");

        // Query
        List<LinkedHashMap<String, Object>> rows = queryRows(conn,
            "SELECT name, email FROM users WHERE name = {userName}");
        System.out.println(rows);  // [{NAME=Alice, EMAIL=alice@example.com}]

        conn.close();
    }
}
```

## String Templates

### Simple Mode (Default)

By default, placeholders use `{name}` syntax — no `$` prefix required. This is controlled by the `requireLeadingDollar` flag, which defaults to `false`.

```java
String name = "Alice";
int age = 30;
f("{name} is {age}");  // "Alice is 30"
```

The `${name}` syntax still works in simple mode, so existing code is unaffected:

```java
f("${name} is ${age}");  // "Alice is 30"
```

To require the `$` prefix (legacy behavior), create a `TemplateExpander` instance:

```java
TemplateExpander legacy = new TemplateExpander().setRequireLeadingDollar(true);
legacy.f("Hello {name}! ${name}");  // "Hello {name}! Alice"
```

### Escaping

Use `\{` to produce a literal `{` that is not treated as a placeholder. Since the closing `}` is only special inside a placeholder, it never needs escaping:

```java
int x = 42;
f("val={x}, json=\\{\"key\": 1}");  // "val=42, json={\"key\": 1}"
```

Use `\\` for a literal backslash:

```java
f("path=C:\\\\Users");  // "path=C:\Users"
```

A backslash before any other character is passed through as-is.

Dollar escaping with `$$` also still works:

```java
int price = 100;
f("Price: $${price}");  // "Price: $100"
```

### Format Specifiers

Append a format after a colon — uses `String.format()` syntax:

```java
double pi = 3.14159;
f("pi = {pi:.2f}")          // "pi = 3.14"

int code = 42;
f("code = {code:05d}")      // "code = 00042"

String s = "hello";
f("{s:S}")                   // "HELLO"
```

### Nested Templates

`{{name}}` looks up a variable, treats its value as a template, and interpolates it:

```java
String greeting = "Hello {name}!";
String name = "Alice";
f("{{greeting}}")  // "Hello Alice!"
```

The `${{name}}` syntax also works:

```java
f("${{greeting}}")  // "Hello Alice!"
```

This is useful for assembling prompts or configuration from parts.

### Optional Placeholders

`{?name}` suppresses output when the value is `null`. When a null optional placeholder is followed by a newline (`\n` or `\r\n`), the newline is also consumed to prevent blank lines in multi-line templates. Controlled by the `optionalPlaceholders` flag (default `true`).

```java
String name = "Alice";
f("Hello {?name}!");  // "Hello Alice!"

String name = null;
f("Hello {?name}!");  // "Hello !"
```

This is especially useful for multi-line templates where null values should not leave blank lines:

```java
String line1 = "header";
String line2 = null;
String line3 = "footer";
f("{line1}\n{?line2}\n{line3}\n");  // "header\nfooter\n"
```

When `line2` is null, both the placeholder and its trailing newline are removed, so the output jumps directly from `header` to `footer` with no blank line in between.

### Container Expansion

Arrays and `Collection` instances render as `[a, b, c]` instead of their default `toString()`. Controlled by the `expandContainers` flag (default `true`).

```java
String[] arr = {"a", "b", "c"};
f("{arr}");  // "[a, b, c]"

int[] nums = {1, 2, 3};
f("{nums}");  // "[1, 2, 3]"

List<String> items = Arrays.asList("x", "y", "z");
f("{items}");  // "[x, y, z]"

String[] empty = {};
f("{empty}");  // "[]"
```

To disable container expansion (reverting to `Object.toString()`), create a `TemplateExpander` instance:

```java
TemplateExpander noContainers = new TemplateExpander().setExpandContainers(false);
String[] arr = {"a", "b"};
noContainers.f("{arr}");  // "[Ljava.lang.String;@..."
```

### Member/Method Access

Dotted expressions like `{user.name}` access fields and methods on objects. The resolution chain for a property `name` is:

1. Public field `name`
2. Accessor method `name()` (record-style)
3. JavaBean getter `getName()`
4. Boolean getter `isName()`

Controlled by the `memberAccess` flag (default `true`).

```java
// Given:
public class User {
    public String name;
    private int age;
    private boolean active;
    public int getAge() { return age; }
    public boolean isActive() { return active; }
    public String greeting() { return "Hello, " + name; }
}

User user = new User("Alice", 30, true);

f("{user.name}");      // "Alice"       — public field
f("{user.age}");       // "30"          — getAge() getter
f("{user.active}");    // "true"        — isActive() getter
```

Record-style accessors (methods named after the field) are also supported:

```java
// Given:
public class Point {
    private final int x, y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public int x() { return x; }
    public int y() { return y; }
}

Point p = new Point(10, 20);
f("({p.x}, {p.y})");  // "(10, 20)"
```

Chained access works for nested objects:

```java
Person person = new Person(new Address("Springfield"));
f("{person.address.city}");  // "Springfield"
```

If any intermediate value in the chain is `null`, the result is `"null"`. Combine with `{?...}` to suppress null chains entirely:

```java
Person person = new Person(null);
f("{person.address.city}");     // "null"
f("{?person.address.city}");    // "" (suppressed)
```

Explicit method calls with `()` are also supported:

```java
User user = new User("Alice", 30, true);
f("{user.greeting()}");  // "Hello, Alice"

String msg = "hello";
f("{msg.length()}");     // "5"
```

## SQL Templates

### Why SQL Templates?

Every SQL injection vulnerability has the same root cause: user-supplied data is concatenated into a SQL string, allowing it to become part of the query structure. Prepared statements solve this, but developers must manually synchronize `?` placeholders with `setObject()` calls — a tedious, error-prone process.

SimpleJavaTemplates eliminates this entire class of bugs:

```java
String userInput = "'; DROP TABLE users; --";

// With SimpleJavaTemplates:
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT * FROM users WHERE name = {userInput}");
// Executes: SELECT * FROM users WHERE name = ?
// Bound parameter: "'; DROP TABLE users; --"
// Result: 0 rows. Table is safe.
```

**SQL injection is structurally impossible** because there is no path by which a value can become part of the query text. The library replaces each `{name}` with `?` to build the parameterized query string, then binds the values from the local variable map via `setObject()`. The separation between "static template fragments" and "dynamic values" maps directly onto JDBC's `PreparedStatement` separation between "query text" and "bound parameters."

This is not input sanitization or escaping — it is a fundamentally different architecture where injection cannot occur.

### SQL IN-Clause Expansion

When a placeholder in a SQL template resolves to an array or `Collection`, it automatically expands to `(?, ?, ...)` with each element bound as an individual parameter. This makes `WHERE id IN {ids}` queries safe and easy:

```java
long[] ids = {1, 2, 3};
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT * FROM users WHERE id IN {ids}");
// Executes: SELECT * FROM users WHERE id IN (?, ?, ?)
// Bound parameters: 1, 2, 3
```

It works with any array type or `Collection`:

```java
List<String> names = Arrays.asList("Alice", "Bob");
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT * FROM users WHERE name IN {names}");
// Executes: SELECT * FROM users WHERE name IN (?, ?)
// Bound parameters: "Alice", "Bob"
```

Empty containers expand to `(NULL)` to produce valid SQL:

```java
long[] ids = {};
queryRows(conn, "SELECT * FROM users WHERE id IN {ids}");
// Executes: SELECT * FROM users WHERE id IN (NULL)
```

### sql() — Build a PreparedStatement

Returns a `PreparedStatement` with parameters already bound. You execute it yourself:

```java
String status = "active";
PreparedStatement ps = sql(conn,
    "SELECT * FROM users WHERE status = {status}");
ResultSet rs = ps.executeQuery();
// ... use rs ...
rs.close();
ps.close();
```

### query() — Execute and Return a ResultSet

Executes the query and returns the `ResultSet`. The underlying statement auto-closes when the `ResultSet` is closed:

```java
String name = "Alice";
ResultSet rs = query(conn, "SELECT email FROM users WHERE name = {name}");
if (rs.next()) {
    System.out.println(rs.getString("email"));
}
rs.close();
```

### queryRows() — Materialize All Rows

Executes the query, reads all rows into a `List<LinkedHashMap<String, Object>>`, and closes everything. Column order is preserved:

```java
String dept = "Engineering";
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT name, email, hire_date FROM employees WHERE department = {dept}");

for (LinkedHashMap<String, Object> row : rows) {
    // Keys are in SELECT order: NAME, EMAIL, HIRE_DATE
    System.out.println(row);
}
```

### queryFirst() — Fetch a Single Row

Returns the first row as a `LinkedHashMap<String, Object>`, or `null` if no rows match:

```java
long userId = 42;
LinkedHashMap<String, Object> user = queryFirst(conn,
    "SELECT name, email FROM users WHERE id = {userId}");
if (user != null) {
    System.out.println(user.get("NAME"));
}
```

### update() — Execute DML

Executes an `UPDATE`, `DELETE`, or other DML statement and returns the affected row count:

```java
String newStatus = "inactive";
long userId = 42;
int affected = update(conn,
    "UPDATE users SET status = {newStatus} WHERE id = {userId}");
System.out.println(affected + " row(s) updated");
```

### insert() — Execute an INSERT

Executes an `INSERT` statement:

```java
String name = "Bob";
String email = "bob@example.com";
insert(conn, "INSERT INTO users (name, email) VALUES ({name}, {email})");
```

### insertAndReturnLongKey() — INSERT with Generated Key

Executes an `INSERT` and returns the auto-generated key as a `long`:

```java
String name = "Bob";
String email = "bob@example.com";
long newId = insertAndReturnLongKey(conn,
    "INSERT INTO users (name, email) VALUES ({name}, {email})");
System.out.println("Created user with id: " + newId);
```

### Implicit Connection

Every SQL method has an overload that omits the `Connection` parameter. The library automatically finds a `Connection` from your local variables (preferring variables named `conn` or `connection`):

```java
Connection conn = dataSource.getConnection();

// These two are equivalent:
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT * FROM users WHERE status = {status}");

List<LinkedHashMap<String, Object>> rows = queryRows(
    "SELECT * FROM users WHERE status = {status}");
// conn was found automatically from the local variable scope
```

This makes the code even cleaner — `conn` is just a local variable sitting in scope, and the library reaches in and uses it through the same side-band channel that provides the SQL parameter values.

If no `Connection` is found, a helpful error message lists your available locals with their types:

```
TemplateException: queryRows() requires a java.sql.Connection in scope.
No local variable of type Connection found.
Available locals: userId (Integer), status (String), logger (Logger).
Either declare a Connection local, or use the explicit overload: queryRows(conn, template).
```

## SQL Method Reference

| Method | Returns | Description |
|--------|---------|-------------|
| `sql(conn, template)` | `PreparedStatement` | Build and bind; caller executes |
| `query(conn, template)` | `ResultSet` | Execute SELECT, return cursor |
| `queryRows(conn, template)` | `List<LinkedHashMap>` | Materialize all rows |
| `queryFirst(conn, template)` | `LinkedHashMap` or `null` | Fetch first row |
| `update(conn, template)` | `int` | Execute DML, return affected count |
| `insert(conn, template)` | `void` | Execute INSERT |
| `insertAndReturnLongKey(conn, template)` | `long` | Execute INSERT, return generated key |

All methods also have a `(template)` overload that discovers `Connection` from local variables.

## TemplateExpander Configuration

The global `TemplateExpander` instance uses sensible defaults. To customize behavior, create your own instance with fluent setters:

| Flag | Default | Description |
|------|---------|-------------|
| `requireLeadingDollar` | `false` | When `false`, `{name}` works without `$`. When `true`, only `${name}` is recognized. |
| `optionalPlaceholders` | `true` | Enables `{?name}` syntax to suppress null values. |
| `expandContainers` | `true` | Arrays and Collections render as `[a, b, c]` instead of `toString()`. |
| `memberAccess` | `true` | Enables dotted expressions like `{user.name}` and `{user.greeting()}`. |

```java
TemplateExpander custom = new TemplateExpander()
    .setRequireLeadingDollar(true)
    .setExpandContainers(false);
custom.f("Hello ${name}");
```

## How It Works

SimpleJavaTemplates is a [Java agent](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html) that transforms bytecode at class-load time using [ASM](https://asm.ow2.io/). When it sees a call to an annotated method like `f()` or `queryRows()`:

1. It captures the caller's local variable table (names, types, scopes)
2. At each call site, it injects bytecode to read all in-scope local variables into a `Map<String, Object>`
3. It redirects the call to a synthetic implementation that receives this map

The caller's source code is unchanged — no reflection, no annotations on the caller, no manual parameter passing. The agent does all the work at class-load time.

### Scope Resolution

The agent correctly handles Java's scoping rules:

- Variables in nested blocks shadow outer variables of the same name
- Variables from completed blocks are not visible
- The same slot reused across sibling scopes resolves to the correct value
- `this` is available in instance methods

### Requirements

- **Java 8+** (compiled with source/target 1.8)
- **Debug info**: Source must be compiled with `-g` or `-g:vars` so the `LocalVariableTable` is present (this is the default for most build tools)
- **Agent flag**: `-javaagent:SimpleJavaTemplates.jar` must be on the JVM command line

## Building from Source

```bash
mvn clean package         # Build the shaded jar and compile tests

# Run tests (requires the agent)
java -javaagent:target/SimpleJavaTemplates.jar \
     -cp target/SimpleJavaTemplates.jar:target/test-classes \
     ai.jacc.simplejavatemplates.smoketest.SmokeTest

java -javaagent:target/SimpleJavaTemplates.jar \
     -cp target/SimpleJavaTemplates.jar:target/test-classes \
     ai.jacc.simplejavatemplates.smoketest.ScopeResolutionTest
```

## License

SimpleJavaTemplates is released under the [MIT No Attribution](LICENSE)
license — a minimalist MIT variant that does not even require retention of
the copyright notice. Use it however you like, in any project, commercial or
otherwise.

## Using SimpleJavaTemplates with DurableJavaThreads

SimpleJavaTemplates is designed to coexist cleanly with
[DurableJavaThreads](https://github.com/hooji/DurableJavaThreads). When both
libraries are on your application classpath, you only need to specify the
`DurableJavaThreads` agent on the command line:

```bash
java -javaagent:durable-threads-1.4.1.jar \
     -agentlib:jdwp=transport=dt_socket,server=y,suspend=n \
     --add-modules jdk.jdi,java.management \
     -cp SimpleJavaTemplates.jar:durable-threads-1.4.1.jar:your-app.jar \
     com.example.Main
```

DurableJavaThreads detects SimpleJavaTemplates on the classpath by
fully-qualified class name and auto-chains its agent for you, in the correct
load order. You do not need a second `-javaagent` flag. Passing one
explicitly is also fine — it becomes a no-op via
`SimpleJavaTemplatesAgent.loaded`.
