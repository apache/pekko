# Akka → Pekko Migration Checklist
## Time Range: 2022-09-01 to 2022-10-20

**Source:** [akka/akka-core](https://github.com/akka/akka-core) (BSL → Apache 2.0 ≥3 years old)  
**Target:** [apache/pekko](https://github.com/apache/pekko)

### Summary
- Total commits in range: 98
- Migrated: 1 ✅
- Skipped (already in Pekko / not applicable / user skip): 97
- Remaining: 0

**Final verdict:** After exhaustive verification, only 1 commit required migration to pekko.
All other commits were either already present in pekko (often ported by He-Pin, the pekko fork owner),
or were not applicable (docs-only, akka-specific, infra/build, version bumps, etc.).

---

## Migration Progress

| # | Orig # | Upstream Hash | Subject | Status | Pekko Commit | PR |
|---|--------|--------------|---------|--------|--------------|----|
| 1 | #22 | [12ecd429](https://github.com/akka/akka-core/commit/12ecd4298939fdaac4d1daa5ba983b0580e901bf) | Make Source.range simpler | ⏭️ SKIP | - | Already in pekko |
| 2 | #48 | [c0843a1c](https://github.com/akka/akka-core/commit/c0843a1cffebcc1649b00ceb64059b7bfc1759a8) | ConcurrentHashMap for async callbacks | ⏭️ SKIP | - | Already in pekko |
| 3 | #50 | [7f9e27c4](https://github.com/akka/akka-core/commit/7f9e27c4236bffe9797474eb5984c884038dd98b) | Add combineSeq to Source and Sink | ⏭️ SKIP | - | Already in pekko @since 1.1.0 |
| 4 | #55 | [ae42d9d0bc](https://github.com/akka/akka-core/commit/ae42d9d0bc78ccd53bf01976710ef960c9226486) | Change default persistence plugin-dispatcher | ⏭️ SKIP | - | Already in pekko reference.conf |
| 5 | #57 | [2eebad8f](https://github.com/akka/akka-core/commit/2eebad8febe22f510dd2a10b9a757f81f552c4ad) | Add AbstractOnMessageBehavior (Java typed) | ⏭️ SKIP | - | User decision |
| 6 | #60 | [a928c282](https://github.com/akka/akka-core/commit/a928c28233169b59cc482a9f0c679227e171db40) | Add Java ExternalShardAllocation example | ⏭️ SKIP | - | Already in pekko |
| 7 | #66 | [1215fb8d](https://github.com/akka/akka-core/commit/1215fb8dcdbb9e48e95e653586073dddb171f0ac) | Fix docs for pruning-marker-time-to-live | ⏭️ SKIP | - | Already in pekko |
| 8 | #67 | [77d757c4](https://github.com/akka/akka-core/commit/77d757c4ec74ecb231256f5b76f7bdd2fc797b7a) | Silence serializer warnings for grpc/http | ✅ DONE | [1b9394ece8](https://github.com/apache/pekko/commit/1b9394ece8) | [#2716](https://github.com/apache/pekko/pull/2716) |
| 9 | #69 | [fe82c695](https://github.com/akka/akka-core/commit/fe82c695f4ef106a08ff96cb56c6b032ed065d4d) | Fix replicated event sourced snapshot bug | ⏭️ SKIP | - | Already in pekko |
| 10 | #74 | [e9f888d4](https://github.com/akka/akka-core/commit/e9f888d4ca9ef80baf8d583d85613eb10b9d7cfd) | Fix statefulMap double-close on cancel | ⏭️ SKIP | - | Already in pekko (different impl) |
| 11 | #85 | [1960941a](https://github.com/akka/akka-core/commit/1960941a111ec93acc392cee531461c98cf29727) | Fix DeletedDurableState emission | ⏭️ SKIP | - | Already in pekko |
| 12 | #86 | [99e811b8](https://github.com/akka/akka-core/commit/99e811b89be0ceccf0a94919972dc6b927922dab) | Fix record timestamp not reused | ⏭️ SKIP | - | Already in pekko |
| 13 | #87 | [e7448f8d](https://github.com/akka/akka-core/commit/e7448f8d990ca005312c257a1721a7eb9b966e7e) | Add UnpersistentBehavior (large feature) | ⏭️ SKIP | - | User decision |
| 14 | #89 | [d6769042](https://github.com/akka/akka-core/commit/d6769042dc7b5050f853be0b557c091cf1b38e66) | Document Java 17 add-opens flags | ⏭️ SKIP | - | Already in pekko |
| 15 | #90 | [4601c1b8](https://github.com/akka/akka-core/commit/4601c1b85f27a5415f35a99769182bb8edb254b1) | Fix UnpersistentBehavior event order | ⏭️ SKIP | - | User decision (depends on #87) |
| 16 | #97 | [e24cdfaf](https://github.com/akka/akka-core/commit/e24cdfaf2d0b9ebc58bb7c8b055ee4095009390b) | Fix RecipeAdhocSource test | ⏭️ SKIP | - | Already in pekko |

**Status legend:** ⬜ PENDING | 🔄 IN PROGRESS | ✅ DONE | ⏭️ SKIPPED | ❌ FAILED

---

## Skip Log (non-trivial skips explained)

| # | Hash | Subject | Category | Reason |
|---|------|---------|----------|--------|
| 1 | 12ecd429 | Source.range simpler | SKIP_ALREADY_IN_PEKKO | Same impl already in pekko |
| 2 | c0843a1c | ConcurrentHashMap async callbacks | SKIP_ALREADY_IN_PEKKO | Already uses ConcurrentHashMap.newKeySet() |
| 3 | 7f9e27c4 | combineSeq / combine(Seq) | SKIP_ALREADY_IN_PEKKO | All overloads present @since 1.1.0 |
| 4 | ae42d9d0bc | Persistence plugin-dispatcher | SKIP_ALREADY_IN_PEKKO | Fallback defaults already corrected in pekko reference.conf |
| 5 | 2eebad8f | AbstractOnMessageBehavior | SKIP_USER | User opted not to migrate |
| 6 | a928c282 | ExternalShardAllocation Java example | SKIP_ALREADY_IN_PEKKO | withAllocationStrategy already in pekko test |
| 7 | 1215fb8d | pruning-marker-time-to-live docs | SKIP_ALREADY_IN_PEKKO | Already correct in pekko distributed-data.md |
| 8 | fe82c695 | Replicated snapshot replay | SKIP_ALREADY_IN_PEKKO | getOrElse(originReplicaId,0L) already in Running.scala |
| 9 | e9f888d4 | statefulMap double-close | SKIP_ALREADY_IN_PEKKO | Pekko uses needInvokeOnCompleteCallback guard; equivalent fix, allows null states by design |
| 10 | 1960941a | DeletedDurableState emission | SKIP_ALREADY_IN_PEKKO | deleteObject already emits publisher notification |
| 11 | 99e811b8 | Record timestamp reuse | SKIP_ALREADY_IN_PEKKO | Already uses Record(...) construction (fresh timestamp) |
| 12 | e7448f8d | UnpersistentBehavior feature | SKIP_USER | User opted not to migrate large feature |
| 13 | d6769042 | Java 17 add-opens docs | SKIP_ALREADY_IN_PEKKO | All 4 docs files already updated |
| 14 | 4601c1b8 | UnpersistentBehavior event order | SKIP_USER | Depends on #87 (also skipped) |
| 15 | e24cdfaf | RecipeAdhocSource test | SKIP_ALREADY_IN_PEKKO | classOf[BackpressureTimeoutException] already in pekko (more precise) |
