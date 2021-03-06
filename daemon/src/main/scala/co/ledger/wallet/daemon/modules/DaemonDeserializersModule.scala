package co.ledger.wallet.daemon.modules

import co.ledger.wallet.daemon.models.coins.BitcoinNetworkParamsView
import co.ledger.wallet.daemon.models.{CurrencyFamily, CurrencyView, WalletView, UnitView => ModelUnit}
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.collection.JavaConverters._

class DaemonDeserializersModule extends SimpleModule {
  addDeserializer(classOf[CurrencyView], new Deserializers.CurrencyDeserializer)
  addDeserializer(classOf[WalletView], new Deserializers.WalletDeserializer)
}

object Deserializers {

  class WalletDeserializer extends JsonDeserializer[WalletView] {

    override def deserialize(jp: JsonParser, ctxt: DeserializationContext): WalletView = {
      val node: JsonNode = jp.getCodec.readTree(jp)
      val name = node.get("name").asText()
      val accountCount = node.get("account_count").asInt()
      val balance = node.get("balance").asLong()
      val configuration = mapper.readValue[Map[String, Any]](node.get("configuration").toString, classOf[Map[String, Any]]) // linter:ignore
      val currency = mapper.readValue[CurrencyView](node.get("currency").toString, classOf[CurrencyView])
      WalletView(name, accountCount, balance, currency, configuration)
    }

    private val mapper: ObjectMapper = new ObjectMapper() with ScalaObjectMapper
    private val module = new SimpleModule()
    module.addDeserializer(classOf[CurrencyView], new CurrencyDeserializer)
    mapper.registerModule(module)
    mapper.registerModule(DefaultScalaModule)
  }

  class CurrencyDeserializer extends JsonDeserializer[CurrencyView] {

    override def deserialize(jp: JsonParser, ctxt: DeserializationContext): CurrencyView = {
      val node: JsonNode = jp.getCodec.readTree(jp)
      val name = node.get("name").asText()
      val family = CurrencyFamily.valueOf(node.get("family").asText())
      val bip44CoinType = node.get("bip_44_coin_type").asInt()
      val paymentUriScheme = node.get("payment_uri_scheme").asText()
      val unitsIter = node.path("units").iterator().asScala
      val units = for (unit <- unitsIter) yield mapper.readValue[ModelUnit](unit.toString, classOf[ModelUnit])
      val networkParams = family match {
        case CurrencyFamily.BITCOIN => mapper.readValue[BitcoinNetworkParamsView](node.get("network_params").toString, classOf[BitcoinNetworkParamsView])
        case _ => throw new NotImplementedError()
      }
      CurrencyView(name, family, bip44CoinType, paymentUriScheme, units.toList, networkParams)
    }

    private val mapper: ObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
  }

}