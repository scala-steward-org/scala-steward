package org.scalasteward.core.persistence

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import munit.FunSuite

class DryRunKeyValueStoreTest extends FunSuite {
  private def inMemory(
      initial: Map[String, String]
  ): IO[(Ref[IO, Map[String, String]], KeyValueStore[IO, String, String])] =
    Ref[IO].of(initial).map { ref =>
      val underlying = new KeyValueStore[IO, String, String] {
        override def get(key: String): IO[Option[String]] = ref.get.map(_.get(key))
        override def set(key: String, value: Option[String]): IO[Unit] =
          ref.update(m => value.fold(m - key)(v => m.updated(key, v)))
      }
      (ref, underlying)
    }

  test("set is a no-op and leaves the underlying store untouched") {
    val (a, b, raw) = (for {
      refAndStore <- inMemory(Map("a" -> "1"))
      (ref, underlying) = refAndStore
      store = new DryRunKeyValueStore(underlying)
      _ <- store.set("a", Some("2"))
      _ <- store.set("b", Some("3"))
      a <- store.get("a")
      b <- store.get("b")
      raw <- ref.get
    } yield (a, b, raw)).unsafeRunSync()

    assertEquals(a, Some("1")) // get delegates; original value unchanged by the suppressed set
    assertEquals(b, None) // the suppressed set did not add the key
    assertEquals(raw, Map("a" -> "1")) // underlying store never written
  }

  test("get delegates to the underlying store") {
    val got = (for {
      refAndStore <- inMemory(Map("k" -> "v"))
      (_, underlying) = refAndStore
      store = new DryRunKeyValueStore(underlying)
      got <- store.get("k")
    } yield got).unsafeRunSync()

    assertEquals(got, Some("v"))
  }
}
