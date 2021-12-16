package com.avsystem.commons
package mongo.typed

import com.avsystem.commons.annotation.explicitGenerics
import com.avsystem.commons.meta.MacroInstances
import com.avsystem.commons.mongo.BsonGenCodecs
import com.avsystem.commons.serialization.{GenCodec, GenObjectCodec}

import scala.annotation.compileTimeOnly

trait MongoPolyAdtInstances[D[_]] {
  // needed by MongoAdtFormat.materialize for generic type
  protected final implicit def codecFromFormat[T: MongoFormat]: GenCodec[T] = MongoFormat[T].codec

  def codec[T: GenCodec]: GenObjectCodec[D[T]]
  def format[T: MongoFormat]: MongoAdtFormat[D[T]]
}

abstract class AbstractMongoPolyDataCompanion[Implicits, D[_]](implicits: Implicits)(
  implicit instances: MacroInstances[Implicits, MongoPolyAdtInstances[D]]
) {
  implicit def codec[T: GenCodec]: GenObjectCodec[D[T]] = instances(implicits, this).codec[T]
  implicit def format[T: MongoFormat]: MongoAdtFormat[D[T]] = instances(implicits, this).format[T]

  implicit def isMongoAdtOrSubtype[C <: D[_]]: IsMongoAdtOrSubtype[C] = null

  implicit class macroDslExtensions[T](value: D[T]) {
    @explicitGenerics
    @compileTimeOnly("the .as[Subtype] construct can only be used inside lambda passed to .ref(...) macro")
    def as[C <: D[T]]: C = sys.error("stub")
  }

  @explicitGenerics
  def dsl[T: MongoFormat]: DataTypeDsl[D[T]] = new DataTypeDsl[D[T]] {
    def SelfRef: MongoRef[D[T], D[T]] = MongoRef.RootRef(format[T])
  }
}

/**
  * Like [[MongoDataCompanion]] buf for generic types (with exactly one unbounded type parameter).
  *
  * @example
  * {{{
  *   case class Point[+T](x: T, y: T)
  *   object Point extends MongoPolyDataCompanion[Point] {
  *     def XRef[T: MongoFormat]: MongoPropertyRef[Point[T], T] =
  *       Point[T].ref(_.x)
  *   }
  * }}}
  */
abstract class MongoPolyDataCompanion[D[_]](
  implicit instances: MacroInstances[BsonGenCodecs.type, MongoPolyAdtInstances[D]]
) extends AbstractMongoPolyDataCompanion[BsonGenCodecs.type, D](BsonGenCodecs)
