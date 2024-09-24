# list-lookup-zio-cache

Based on Scala v3.3.1 and [ZIO Cache v0.2.3](https://github.com/zio/zio-cache/blob/v0.2.3/zio-cache/)

An extension to [zio.cache.Cache](https://github.com/zio/zio-cache/blob/v0.2.3/zio-cache/shared/src/main/scala/zio/cache/Cache.scala)
- Supports `getAll(Seq[Key])` and `lookup(Seq[Key])`
- Only calls `lookup` for elements of `Seq[Key]` not in cache
- Continues to support `get(Key)` as a size 1 seq
- Does not break any existing ZIO cache features