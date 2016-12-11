package io.tailrec.sbt.severus

import com.amazonaws.{AmazonClientException, AmazonWebServiceRequest, ClientConfiguration}
import com.amazonaws.regions.{Region, RegionUtils, Regions}
import com.amazonaws.retry.{PredefinedRetryPolicies, RetryPolicy}
import com.amazonaws.services.apigateway.AmazonApiGatewayClient
import com.amazonaws.services.apigateway.model._

import scala.collection.JavaConverters._
/**
  * Created by Hussachai on 8/21/2016.
  */
object Test extends App with SeverusAws {
  val PACKAGE_VARIABLE = "<PACKAGE>"
  val INPUT_TEMPLATE = "{\n" +
    "  \"package\": \"" + PACKAGE_VARIABLE + "\",\n" +
    "  \"pathtemplate\": \"$context.resourcePath\",\n" +
    "  \"method\": \"$context.httpMethod\",\n" +
    "  \"requestbody\": \"$util.escapeJavaScript($input.json('$'))\",\n" +
    "      #foreach($elem in $input.params().keySet())\n" +
    "        \"$elem\": {\n" +
    "            #foreach($innerElem in $input.params().get($elem).keySet())\n" +
    "        \"$innerElem\": \"$util.urlDecode($input.params().get($elem).get($innerElem))\"#if($foreach.hasNext),#end\n" +
    "      #end\n" +
    "        }#if($foreach.hasNext),#end\n" +
    "      #end\n" +
    "}";
  println(INPUT_TEMPLATE)

  val retryCondition = new RetryPolicy.RetryCondition() {
    @Override
    def shouldRetry(amazonWebServiceRequest: AmazonWebServiceRequest, amazonClientException: AmazonClientException, i: Int): Boolean = {
      if (amazonClientException.isInstanceOf[TooManyRequestsException]) {
        true
      } else {
        PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION.shouldRetry(amazonWebServiceRequest, amazonClientException, i)
      }
    }
  }
  val retryPolicy = new RetryPolicy(retryCondition,
    PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
    10, true)
  val clientConfig = new ClientConfiguration().withRetryPolicy(retryPolicy)
  val apiGatewayClient = new AmazonApiGatewayClient(credentialsProvider, clientConfig)
  apiGatewayClient.setRegion(Region.getRegion(Regions.fromName("us-east-1")))

  apiGatewayClient.createRestApi(new CreateRestApiRequest().withName("hello"))
//  val apiKeyReq = new CreateApiKeyRequest().withName("test")
//    .withEnabled(true)
//    .withStageKeys(List(new StageKey().withRestApiId("k6xiqf7156").withStageName("prod")).asJava)
//  println(apiGatewayClient.createApiKey(apiKeyReq).getId)

}
