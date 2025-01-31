package io.scalac.mesmer.extension.service

import io.scalac.mesmer.core.model.Path

object DummyCommonRegexPathService extends PathService {

  def template(path: Path): Path = path match {
    case get if get.contains("balance")           => "/api/v1/account/{uuid}/balance"
    case withdraw if withdraw.contains("balance") => "/api/v1/account/{uuid}/withdraw/{num}"
    case deposit if deposit.contains("balance")   => "/api/v1/account/{uuid}/deposit/{num}"
    case _                                        => "other"
  }
}
