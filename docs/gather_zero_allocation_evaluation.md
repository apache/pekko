# Pekko Gather/Zero-Allocation Operator 评估报告

## 背景与目标
- 目标：分析现有 statefulMap/statefulMapConcat 的分配情况，评估是否有必要引入类似 JDK 24 Gatherer 的 zero-allocation 操作符，并明确不能破坏现有 API。
- 范围：仅分析 Pekko Stream 当前实现与 JDK 24 Gatherer、SmallRye Mutiny 等对比。

## 现状分析
### statefulMap
- API：`(S, In) => (S, Out)`
- 每个元素分配一个 Tuple2（(S, Out)），为 API 设计所致。
- 不能避免 per-element 分配。

### statefulMapConcat
- API：`(S, In) => (S, Iterable[Out])`
- 每个元素分配 Iterable 和 Iterator。
- 也是 API 设计决定，无法避免。

## JDK 24 Gatherer 对比
- JDK 24 Gatherer 通过 mutable state + 直接下游 push，理论上可实现零分配。
- 参考实现仍有部分分配（如 lambda/闭包等）。
- SmallRye Mutiny 也有类似设计，但仍有微小分配。

## 结论与建议
- 若要实现 zero-allocation，需设计全新 API，不能破坏 statefulMap 现有语义。
- 建议：如需极致性能，可新增 opt-in gather-like 操作符，保留现有 API。
- 现有 statefulMap/statefulMapConcat 的分配为 API 设计本质，非实现缺陷。

## 参考文件
- stream/src/main/scala/org/apache/pekko/stream/impl/fusing/Ops.scala
- JDK 24 Gatherer 官方文档
- SmallRye Mutiny 源码

## 评估人
- 由 Pekko 迁移小组（gpt-4.1）完成
- 评估时间：2026-03-28

---
如需详细设计/原型实现，请补充需求。