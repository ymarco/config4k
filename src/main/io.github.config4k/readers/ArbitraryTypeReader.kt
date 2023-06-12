package io.github.config4k.readers

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.extract
import io.github.config4k.getGenericMap
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType


internal class ArbitraryTypeReader(clazz: ClassContainer) : Reader<Any>({ config, path ->
  extractWithParameters(clazz, config, path)
})

internal fun extractWithParameters(
  clazz: ClassContainer,
  config: Config,
  parentPath: String = ""
): Any {
  val constructor = clazz.mapperClass.primaryConstructor!!
  val parentConfig by lazy { config.extract<Config>(parentPath) }
  val missingParams = mutableListOf<KParameter>()
  val parameters_ = constructor.parameters.associateWith { param ->
    val type = param.type.javaType
    val classContainer: ClassContainer = when (type) {
      is ParameterizedType -> ClassContainer(
        (type.rawType as Class<*>).kotlin,
        getGenericMap(type, clazz.typeArguments)
      )

      is Class<*> -> ClassContainer(type.kotlin)
      else -> requireNotNull(clazz.typeArguments[type.typeName]) { "couldn't find type argument for ${type.typeName}" }
    }
    return@associateWith SelectReader.getReader(classContainer)
      .let { read ->
        try {
          read(if (parentPath.isEmpty()) config else parentConfig, param.name!!)
        } catch (e: MissingFieldsException) {
          missingParams += e.missingFields
          e.readResult
        }
      }

  }.filterValues { value -> value != null }
  val parameters = omitValue(parameters_, config, parentPath)
  if (clazz.mapperClass.visibility == KVisibility.PRIVATE) {
    constructor.isAccessible = true
  }
  missingParams += constructor.parameters - parameters.keys
  val res = constructor.callBy(parameters)
  missingParams.ifEmpty {
    return res
  }
  throw MissingFieldsException(clazz, missingParams, res)
}

// if config doesn't have corresponding value, the value is omitted
internal fun omitValue(
  map: Map<KParameter, Any?>,
  config: Config,
  parentPath: String
): Map<KParameter, Any?> =
  map.filterNot { (param, _) ->
    val path = if (parentPath.isEmpty()) param.name
    else "$parentPath.${param.name}"
    param.isOptional && !config.hasPathOrNull(path)
  }

