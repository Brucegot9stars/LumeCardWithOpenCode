# JVM notes

The JVM executes Java bytecode. A Kotlin `val` binding cannot be reassigned;
this does not make the referenced object immutable. Kotlin's `List` is read-only,
not a guarantee that the underlying collection cannot change.

```kotlin
val names = mutableListOf("Ada")
names.add("Lin") // Allowed: the binding still refers to the same list.
```
