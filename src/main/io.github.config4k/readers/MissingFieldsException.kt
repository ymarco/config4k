package io.github.config4k.readers

import io.github.config4k.ClassContainer
import kotlin.reflect.KParameter

class MissingFieldsException(
  val clazz: ClassContainer,
  val missingFields: List<KParameter>,
  val readResult: Any
) : Exception("Missing parameters when creating ${clazz.mapperClass.qualifiedName}") {

}
