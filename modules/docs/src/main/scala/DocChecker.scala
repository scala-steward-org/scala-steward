/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.circe.config.parser
import io.circe.{Decoder, Encoder}
import munit.diff.Diff

object DocChecker {
  def verifyParsedEqualsEncoded[A](input: String)(implicit
      decoder: Decoder[A],
      encoder: Encoder[A]
  ): Unit = {
    val res = parser.parse(input).flatMap { jsonFromStr =>
      decoder.decodeJson(jsonFromStr).flatMap { a =>
        val jsonFromObj = encoder.apply(a)
        val diff = Diff(
          jsonFromObj.deepDropNullValues.spaces2SortKeys,
          jsonFromStr.deepDropNullValues.spaces2SortKeys
        )
        if (diff.isEmpty) Right(())
        else {
          val msg = "Diff between parsed input (-) and encoded object (+):\n" + diff.unifiedDiff
          Left(new Throwable(msg))
        }
      }
    }
    res.fold(throw _, identity)
  }
}
