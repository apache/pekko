---
project.description: Binary compatibility across Apache Pekko versions.
---
# Binary Compatibility Rules

Apache Pekko maintains and verifies *backwards binary compatibility* across versions of modules.

In the rest of this document whenever *binary compatibility* is mentioned "*backwards binary compatibility*" is meant
(as opposed to forward compatibility).

This means that the new JARs are a drop-in replacement for the old one 
(but not the other way around) as long as your build does not enable the inliner (Scala-only restriction).

Because of this approach applications can upgrade to the latest version of Pekko
even when @ref[intermediate satellite projects are not yet upgraded](../project/downstream-upgrade-strategy.md)

## Binary compatibility rules explained

Binary compatibility is maintained between:

 * **minor** and **patch** versions

Binary compatibility is **NOT** maintained between:

 * **major** versions
 * any versions of **may change** modules – read @ref:[Modules marked "May Change"](may-change.md) for details
 * a few notable exclusions explained below

Specific examples:

```
OK:  1.4.0 --> 1.5.x
OK:  1.5.0 --> 1.6.x
NO:  1.x.y --x 2.x.y
OK:  2.0.0 --> 2.0.1 --> ... --> 2.0.n
OK:  2.0.n --> 2.1.0 --> ... --> 2.1.n
OK:  2.1.n --> 2.2.0 ...
     ...
```

### Cases where binary compatibility is not retained

If a security vulnerability is reported in Pekko or a transient dependency of Pekko and it cannot be solved without breaking binary compatibility then fixing the security issue is more important. In such cases binary compatibility might not be retained when releasing a minor version. Such exception is always noted in the release announcement.

We do not guarantee binary compatibility with versions that are EOL, though in
practice this does not make a big difference: only in rare cases would a change
be binary compatible with recent previous releases but not with older ones.

Some modules are excluded from the binary compatibility guarantees, such as:

 * `*-testkit` modules - since these are to be used only in tests, which usually are re-compiled and run on demand
 * `*-tck` modules - since they may want to add new tests (or force configuring something), in order to discover possible failures in an existing implementation that the TCK is supposed to be testing. Compatibility here is not *guaranteed*, however it is attempted to make the upgrade process as smooth as possible.
 * all @ref:[may change](may-change.md) modules - which by definition are subject to rapid iteration and change. Read more about that in @ref:[Modules marked "May Change"](may-change.md)
 
### When will a deprecated method be removed entirely

Once a method has been deprecated then the guideline* is that it will be kept, at minimum, for one **full** minor version release. For example, if it is deprecated in version 1.0.2 then it will remain through the rest of 1.0, as well as the entirety of 1.1.

Methods that were deprecated in Akka, before the project fork to Pekko, are being considered for removal in Pekko 1.1.0.

*This is a guideline because in **rare** instances, after careful consideration, an exception may be made and the method removed earlier.

## Mixed versioning is not allowed

Modules that are released together under the Pekko project are intended to be upgraded together.
For example, it is not legal to mix Pekko Actor `1.0.0` with Pekko Cluster `1.0.5` even though
"Pekko `1.0.0`" and "Pekko `1.0.5`" *are* binary compatible. 

This is because modules may assume internals changes across module boundaries, for example some feature
in Clustering may have required an internals change in Actor, however it is not public API, 
thus such change is considered safe.

If you accidentally mix Pekko versions, for example through transitive
dependencies, you might get a warning at run time such as:

```
You are using version 1.0.6 of Pekko, but it appears you (perhaps indirectly) also depend on older versions 
of related artifacts. You can solve this by adding an explicit dependency on version 1.0.6 of the 
[pekko-persistence-query] artifacts to your project. Here's a complete collection of detected 
artifacts: (1.0.3, [pekko-persistence-query]), (1.0.6, [pekko-actor, pekko-cluster]).
See also: https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed
```

The fix is typically to pick the highest Pekko version, and add explicit
dependencies to your project as needed. For example, in the example above
you might want to add `pekko-persistence-query` dependency for 1.0.6.

@@@ note

We recommend keeping an `pekkoVersion` variable in your build file, and re-use it for all
included modules, so when you upgrade you can simply change it in this one place.

@@@

The warning includes a full list of Pekko runtime dependencies in the classpath, and the version detected. 
You can use that information to include an explicit list of Pekko artifacts you depend on into your build. If you use
Maven or Gradle, you can include the @ref:[Pekko Maven BOM](../typed/guide/modules.md#actor-library) (bill 
of materials) to help you keep all the versions of your Pekko dependencies in sync. 


## The meaning of "may change"

**May change** is used in module descriptions and docs in order to signify that the API that they contain
is subject to change without any prior warning and is not covered by the binary compatibility promise.
Read more in @ref:[Modules marked "May Change"](may-change.md).

## API stability annotations and comments

Pekko gives a very strong binary compatibility promise to end-users. However some parts of Pekko are excluded 
from these rules, for example internal or known evolving APIs may be marked as such and shipped as part of 
an overall stable module. As general rule any breakage is avoided and handled via deprecation and method addition,
however certain APIs which are known to not yet be fully frozen (or are fully internal) are marked as such and subject 
to change at any time (even if best-effort is taken to keep them compatible).

### The INTERNAL API and *@InternalAPI* marker

When browsing the source code and/or looking for methods available to be called, especially from Java which does not
have as rich of an access protection system as Scala has, you may sometimes find methods or classes annotated with
the `/** INTERNAL API */` comment or the @javadoc[@InternalApi](pekko.annotation.InternalApi) annotation. 

No compatibility guarantees are given about these classes. They may change or even disappear in minor versions,
and user code is not supposed to call them.

Side-note on JVM representation details of the Scala `private[pekko]` pattern that Pekko is using extensively in 
its internals: Such methods or classes, which act as "accessible only from the given package" in Scala, are compiled
down to `public` (!) in raw Java bytecode. The access restriction, that Scala understands is carried along
as metadata stored in the classfile. Thus, such methods are safely guarded from being accessed from Scala,
however Java users will not be warned about this fact by the `javac` compiler. Please be aware of this and do not call
into Internal APIs, as they are subject to change without any warning.

### The `@DoNotInherit` and `@ApiMayChange` markers

In addition to the special internal API marker two annotations exist in Pekko and specifically address the following use cases:

 * @javadoc[@ApiMayChange](pekko.annotation.ApiMayChange) – which marks APIs which are known to be not fully stable yet. Read more in @ref:[Modules marked "May Change"](may-change.md)
 * @javadoc[@DoNotInherit](pekko.annotation.DoNotInherit) – which marks APIs that are designed under a closed-world assumption, and thus must not be
extended outside Pekko itself (or such code will risk facing binary incompatibilities). E.g. an interface may be
marked using this annotation, and while the type is public, it is not meant for extension by user-code. This allows
adding new methods to these interfaces without risking to break client code. Examples of such API are the @scaladoc[FlowOps](pekko.stream.scaladsl.FlowOps)
trait or the Pekko HTTP domain model.

Please note that a best-effort approach is always taken when having to change APIs and breakage is avoided as much as 
possible, however these markers allow to experiment, gather feedback and stabilize the best possible APIs we could build.

## Binary Compatibility Checking Toolchain

Pekko uses the Lightbend maintained [MiMa](https://github.com/lightbend/mima),
for enforcing binary compatibility is kept where it was promised.

All Pull Requests must pass MiMa validation (which happens automatically), and if failures are detected,
manual exception overrides may be put in place if the change happened to be in an Internal API for example.

## Serialization compatibility across Scala versions

Scala does not maintain serialization compatibility across major versions. This means that if Java serialization is used
there is no guarantee objects can be cleanly deserialized if serialized with a different version of Scala.

## Binary Compatibility of dependencies

The above rules apply to Pekko modules themselves. They do not necessarily
apply to dependencies: within a major Pekko component version, we may upgrade a
major version of a dependency. For example, between Pekko Connectors 1.0 and
1.1, we updated from `javax.jms` 1.1 to `javax.jms` 2.0.1 in the JMS component.
This means when you update this component, you may also need to update any
other components that were still built against `javax.jms` 1.1.
