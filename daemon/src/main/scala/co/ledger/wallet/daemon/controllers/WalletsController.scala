package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.services.WalletsService
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, NotEmpty, ValidationResult}

import scala.concurrent.ExecutionContext

class WalletsController @Inject()(walletsService: WalletsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import WalletsController._

  /**
    * End point queries for wallets views in specified pool.
    *
    */
  get("/pools/:pool_name/wallets") {(request: GetWalletsRequest) =>
    info(s"GET wallets $request")
    walletsService.wallets(
      request.user,
      request.pool_name,
      request.offset.getOrElse(DEFAULT_OFFSET),
      request.count.getOrElse(DEFAULT_COUNT))
      .recover {
        case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
          response)
        case e: Throwable => responseSerializer.serializeInternalError(response, e)
      }
  }

  /**
    * End point queries for wallet view in specified pool by it's name.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name") { request: GetWalletRequest =>
    info(s"GET wallet $request")
    walletsService.wallet(request.user, request.pool_name, request.wallet_name).map {
      case Some(view) => responseSerializer.serializeOk(view, response)
      case None => responseSerializer.serializeNotFound(
        Map("response" -> "Wallet doesn't exist", "wallet_name" -> request.wallet_name), response)
    }.recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point to create a instance of wallet within the specified pool.
    *
    */
  post("/pools/:pool_name/wallets") {(request: CreateWalletRequest) =>
    info(s"CREATE wallet $request")
    walletsService.createWallet(request.user, request.pool_name, request.wallet_name, request.currency_name).recover {
      case _: CurrencyNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"-> "Currency not support", "currency_name" -> request.currency_name),
        response)
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object WalletsController {
  private val DEFAULT_OFFSET: Int = 0
  private val DEFAULT_COUNT: Int = 20
  case class GetWalletRequest(
                               @RouteParam pool_name: String,
                               @RouteParam wallet_name: String,
                               request: Request
                             ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, wallet_name: $wallet_name)"
  }

  case class GetWalletsRequest(
                              @RouteParam pool_name: String,
                              @QueryParam offset: Option[Int],
                              @QueryParam count: Option[Int],
                              request: Request
                              ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateOffset: ValidationResult = ValidationResult.validate(offset.isEmpty || offset.get >= 0, "offset: offset can not be less than zero")

    @MethodValidation
    def validateCount: ValidationResult = ValidationResult.validate(count.isEmpty || count.get > 0, "account_index: index can not be less than 1")

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, offset: $offset, count: $count)"
  }

  case class CreateWalletRequest(
                                @RouteParam pool_name: String,
                                @NotEmpty @JsonProperty wallet_name: String,
                                @NotEmpty @JsonProperty currency_name: String,
                                request: Request
                                ) extends RichRequest(request) {
    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, wallet_name: $wallet_name, currency_name: $currency_name)"
  }
}