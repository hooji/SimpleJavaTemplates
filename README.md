# SimpleJavaTemplates

String templates and SQL parameterization for Java, powered by local variable capture.

Write `f("Hello ${name}")` and the library reads `name` directly from your local variables — no manual parameter passing. The same mechanism drives a suite of SQL methods where `${userId}` becomes a bound `?` parameter, making SQL injection **structurally impossible**.

```java
import static ai.jacc.simplejavatemplates.Template.*;

// String templates — reads locals automatically
String name = "Alice";
int age = 30;
String msg = f("${name} is ${age} years old");  // "Alice is 30 years old"

// SQL — values are bound as parameters, never concatenated into the query
Connection conn = dataSource.getConnection();
String dept = "Engineering";
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT name, email FROM employees WHERE department = ${dept}");
```

## Quick Start

### 1. Get the JAR

Download `SimpleJavaTemplates.jar` from the [latest release](https://github.com/hooji/SimpleJavaTemplates/releases), or build from source:

```bash
gradle jar
# Output: build/libs/SimpleJavaTemplates.jar
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
        System.out.println(f("Hello ${name}!"));  // Hello World!

        // --- SQL templates ---
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:demo");
        conn.createStatement().execute(
            "CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(100), email VARCHAR(100))");

        // Insert
        String userName = "Alice";
        String email = "alice@example.com";
        insert(conn, "INSERT INTO users (name, email) VALUES (${userName}, ${email})");

        // Query
        List<LinkedHashMap<String, Object>> rows = queryRows(conn,
            "SELECT name, email FROM users WHERE name = ${userName}");
        System.out.println(rows);  // [{NAME=Alice, EMAIL=alice@example.com}]

        conn.close();
    }
}
```

## String Templates

### Basic Interpolation

`f()` reads local variables from the calling method's scope and substitutes `${name}` placeholders:

```java
String name = "Alice";
int count = 3;
double price = 29.99;

f("${name} bought ${count} items for $$${price}")
// "Alice bought 3 items for $29.99"
```

All Java types are supported: primitives, objects, `null`, and `this` (in instance methods).

### Format Specifiers

Append a format after a colon — uses `String.format()` syntax:

```java
double pi = 3.14159;
f("pi = ${pi:.2f}")          // "pi = 3.14"

int code = 42;
f("code = ${code:05d}")      // "code = 00042"

String s = "hello";
f("${s:S}")                  // "HELLO"
```

### Nested Templates

`${{name}}` looks up a variable, treats its value as a template, and interpolates it:

```java
String greeting = "Hello ${name}!";
String name = "Alice";
f("${{greeting}}")  // "Hello Alice!"
```

This is useful for assembling prompts or configuration from parts.

### Dollar Escaping

Use `$$` to produce a literal `$`:

```java
int price = 100;
f("Price: $$${price}")  // "Price: $100"
```

## SQL Templates

### Why SQL Templates?

Every SQL injection vulnerability has the same root cause: user-supplied data is concatenated into a SQL string, allowing it to become part of the query structure. Prepared statements solve this, but developers must manually synchronize `?` placeholders with `setObject()` calls — a tedious, error-prone process.

SimpleJavaTemplates eliminates this entire class of bugs:

```java
String userInput = "'; DROP TABLE users; --";

// With SimpleJavaTemplates:
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT * FROM users WHERE name = ${userInput}");
// Executes: SELECT * FROM users WHERE name = ?
// Bound parameter: "'; DROP TABLE users; --"
// Result: 0 rows. Table is safe.
```

**SQL injection is structurally impossible** because there is no path by which a value can become part of the query text. The library replaces each `${name}` with `?` to build the parameterized query string, then binds the values from the local variable map via `setObject()`. The separation between "static template fragments" and "dynamic values" maps directly onto JDBC's `PreparedStatement` separation between "query text" and "bound parameters."

This is not input sanitization or escaping — it is a fundamentally different architecture where injection cannot occur.

### sql() — Build a PreparedStatement

Returns a `PreparedStatement` with parameters already bound. You execute it yourself:

```java
String status = "active";
PreparedStatement ps = sql(conn,
    "SELECT * FROM users WHERE status = ${status}");
ResultSet rs = ps.executeQuery();
// ... use rs ...
rs.close();
ps.close();
```

### query() — Execute and Return a ResultSet

Executes the query and returns the `ResultSet`. The underlying statement auto-closes when the `ResultSet` is closed:

```java
String name = "Alice";
ResultSet rs = query(conn, "SELECT email FROM users WHERE name = ${name}");
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
    "SELECT name, email, hire_date FROM employees WHERE department = ${dept}");

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
    "SELECT name, email FROM users WHERE id = ${userId}");
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
    "UPDATE users SET status = ${newStatus} WHERE id = ${userId}");
System.out.println(affected + " row(s) updated");
```

### insert() — Execute an INSERT

Executes an `INSERT` statement:

```java
String name = "Bob";
String email = "bob@example.com";
insert(conn, "INSERT INTO users (name, email) VALUES (${name}, ${email})");
```

### insertAndReturnLongKey() — INSERT with Generated Key

Executes an `INSERT` and returns the auto-generated key as a `long`:

```java
String name = "Bob";
String email = "bob@example.com";
long newId = insertAndReturnLongKey(conn,
    "INSERT INTO users (name, email) VALUES (${name}, ${email})");
System.out.println("Created user with id: " + newId);
```

### Implicit Connection

Every SQL method has an overload that omits the `Connection` parameter. The library automatically finds a `Connection` from your local variables (preferring variables named `conn` or `connection`):

```java
Connection conn = dataSource.getConnection();

// These two are equivalent:
List<LinkedHashMap<String, Object>> rows = queryRows(conn,
    "SELECT * FROM users WHERE status = ${status}");

List<LinkedHashMap<String, Object>> rows = queryRows(
    "SELECT * FROM users WHERE status = ${status}");
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
gradle jar                # Build the shaded jar
gradle compileTestJava    # Compile tests

# Run tests (requires the agent)
java -javaagent:build/libs/SimpleJavaTemplates.jar \
     -cp build/libs/SimpleJavaTemplates.jar:build/classes/java/test \
     ai.jacc.simplejavatemplates.smoketest.SmokeTest

java -javaagent:build/libs/SimpleJavaTemplates.jar \
     -cp build/libs/SimpleJavaTemplates.jar:build/classes/java/test \
     ai.jacc.simplejavatemplates.smoketest.ScopeResolutionTest
```

## License

See [LICENSE](LICENSE) for details.
