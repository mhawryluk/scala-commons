package com.avsystem.commons
package macros.rpc

import com.avsystem.commons.macros.AbstractMacroCommons

import scala.reflect.macros.blackbox

abstract class RPCMacroCommons(ctx: blackbox.Context) extends AbstractMacroCommons(ctx) {

  import c.universe._

  val RpcPackage = q"$CommonsPkg.rpc"
  val RpcNameType: Type = getType(tq"$RpcPackage.rpcName")
  val RpcNameNameSym: Symbol = RpcNameType.member(TermName("name"))
  val AsRealCls = tq"$RpcPackage.AsReal"
  val AsRealObj = q"$RpcPackage.AsReal"
  val AsRawCls = tq"$RpcPackage.AsRaw"
  val AsRawObj = q"$RpcPackage.AsRaw"
  val AsRealRawCls = tq"$RpcPackage.AsRealRaw"
  val AsRealRawObj = q"$RpcPackage.AsRealRaw"
  val OptionLikeCls = tq"$RpcPackage.OptionLike"
  val CanBuildFromCls = tq"$CollectionPkg.generic.CanBuildFrom"

  val RpcArityAT: Type = getType(tq"$RpcPackage.RpcArity")
  val SingleArityAT: Type = getType(tq"$RpcPackage.single")
  val OptionalArityAT: Type = getType(tq"$RpcPackage.optional")
  val MultiArityAT: Type = getType(tq"$RpcPackage.multi")
  val RpcEncodingAT: Type = getType(tq"$RpcPackage.RpcEncoding")
  val VerbatimAT: Type = getType(tq"$RpcPackage.verbatim")
  val AuxiliaryAT: Type = getType(tq"$RpcPackage.auxiliary")
  val MethodTagAT: Type = getType(tq"$RpcPackage.methodTag[_,_]")
  val ParamTagAT: Type = getType(tq"$RpcPackage.paramTag[_,_]")
  val TaggedAT: Type = getType(tq"$RpcPackage.tagged[_]")
  val RpcTagAT: Type = getType(tq"$RpcPackage.RpcTag")
  val RpcImplicitsSym: Symbol = getType(tq"$RpcPackage.RpcImplicitsProvider").member(TermName("implicits"))
  val TypedMetadataType: Type = getType(tq"$RpcPackage.TypedMetadata[_]")
  val MetadataParamStrategyType: Type = getType(tq"$RpcPackage.MetadataParamStrategy")
  val ReifyAT: Type = getType(tq"$RpcPackage.reify")
  val InferAT: Type = getType(tq"$RpcPackage.infer")
  val MetadataPFType: Type = getType(tq"$ScalaPkg.PartialFunction[$StringCls,$TypedMetadataType]")

  val NothingTpe: Type = typeOf[Nothing]
  val StringPFTpe: Type = typeOf[PartialFunction[String, Any]]
  val BIterableTpe: Type = typeOf[Iterable[Any]]
  val BIndexedSeqTpe: Type = typeOf[IndexedSeq[Any]]

  val PartialFunctionClass: Symbol = StringPFTpe.typeSymbol
  val BIterableClass: Symbol = BIterableTpe.typeSymbol
  val BIndexedSeqClass: Symbol = BIndexedSeqTpe.typeSymbol

  def registerCompanionImplicits(rawTpe: Type): Unit =
    companionOf(rawTpe).filter { companion =>
      val typed = c.typecheck(q"$companion.implicits", silent = true)
      typed != EmptyTree && typed.symbol.overrides.contains(RpcImplicitsSym)
    }.foreach { companion =>
      registerImplicitImport(q"import $companion.implicits._")
    }
}

class RPCMacros(ctx: blackbox.Context) extends RPCMacroCommons(ctx)
  with RPCSymbols with RPCMappings with RPCMetadatas {

  import c.universe._

  def rpcAsReal[R: WeakTypeTag, T: WeakTypeTag]: Tree = {
    val raw = RawRpcTrait(weakTypeOf[R].dealias)
    val real = RealRpcTrait(weakTypeOf[T].dealias)
    val mapping = RpcMapping(real, raw, forAsRaw = false, forAsReal = true)

    // must be evaluated before `cachedImplicitDeclarations`, don't inline it into the quasiquote
    val asRealDef = mapping.asRealImpl

    q"""
      new $AsRealCls[${raw.tpe},${real.tpe}] { ${mapping.selfName}: ${TypeTree()} =>
        ..$cachedImplicitDeclarations
        $asRealDef
      }
    """
  }

  def rpcAsRaw[R: WeakTypeTag, T: WeakTypeTag]: Tree = {
    val raw = RawRpcTrait(weakTypeOf[R].dealias)
    val real = RealRpcTrait(weakTypeOf[T].dealias)
    val mapping = RpcMapping(real, raw, forAsRaw = true, forAsReal = false)

    // must be evaluated before `cachedImplicitDeclarations`, don't inline it into the quasiquote
    val asRawDef = mapping.asRawImpl

    q"""
      new $AsRawCls[${raw.tpe},${real.tpe}] { ${mapping.selfName}: ${TypeTree()} =>
        ..$cachedImplicitDeclarations
        $asRawDef
      }
     """
  }

  def rpcAsRealRaw[R: WeakTypeTag, T: WeakTypeTag]: Tree = {
    val raw = RawRpcTrait(weakTypeOf[R].dealias)
    val real = RealRpcTrait(weakTypeOf[T].dealias)
    val mapping = RpcMapping(real, raw, forAsRaw = true, forAsReal = true)

    // these two must be evaluated before `cachedImplicitDeclarations`, don't inline them into the quasiquote
    val asRealDef = mapping.asRealImpl
    val asRawDef = mapping.asRawImpl

    q"""
      new $AsRealRawCls[${raw.tpe},${real.tpe}] { ${mapping.selfName}: ${TypeTree()} =>
        ..$cachedImplicitDeclarations
        $asRealDef
        $asRawDef
      }
     """
  }

  def rpcMetadata[M: WeakTypeTag, T: WeakTypeTag]: Tree = {
    val realRpc = RealRpcTrait(weakTypeOf[T].dealias)
    val metadataTpe = weakTypeOf[M] match { // scalac, why do I have to do this?
      case TypeRef(pre, sym, Nil) =>
        internal.typeRef(pre, sym, List(realRpc.tpe))
      case TypeRef(pre, sym, List(TypeRef(NoPrefix, wc, Nil))) if wc.name == typeNames.WILDCARD =>
        internal.typeRef(pre, sym, List(realRpc.tpe))
      case t => t
    }

    val tree = new RpcMetadataConstructor(metadataTpe).materializeFor(realRpc)
    q"..$cachedImplicitDeclarations; $tree"
  }

  def lazyMetadata(metadata: Tree): Tree =
    q"${c.prefix}($metadata)"
}
